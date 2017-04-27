package zmq.io.mechanism;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.Metadata;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.socket.Sockets;
import zmq.util.Blob;
import zmq.util.Wire;

// Abstract class representing security mechanism.
// Different mechanism extends this class.
public abstract class Mechanism
{
    protected static final String IDENTITY = "Identity";

    protected static final String SOCKET_TYPE = "Socket-Type";

    public static enum Status
    {
        HANDSHAKING,
        READY,
        ERROR
    }

    protected final Options options;

    private Blob identity;
    private Blob userId;

    //  Properties received from ZAP server.
    public final Metadata zapProperties = new Metadata();

    //  Properties received from ZMTP peer.
    public final Metadata zmtpProperties = new Metadata();

    protected final SessionBase session;
    private final Address       peerAddress;

    protected String statusCode;

    protected Mechanism(SessionBase session, Address peerAddress, Options options)
    {
        this.session = session;
        this.options = options;
        this.peerAddress = peerAddress;
    }

    public abstract Status status();

    void setPeerIdentity(String data)
    {
        identity = Blob.createBlob(data.getBytes(ZMQ.CHARSET));
    }

    public Msg peerIdentity()
    {
        Msg msg = new Msg(identity == null ? 0 : identity.size());
        msg.put(identity.data(), 0, identity.size());
        msg.setFlags(Msg.IDENTITY);

        return msg;
    }

    void setUserId(byte[] data)
    {
        userId = Blob.createBlob(data);
        zapProperties.set("User-Id", new String(data, ZMQ.CHARSET));
    }

    public Blob getUserId()
    {
        return userId;
    }

    protected void addProperty(ByteBuffer buf, String name, String value)
    {
        addProperty(buf, name, value.getBytes(ZMQ.CHARSET));
    }

    protected void addProperty(Msg msg, String name, String value)
    {
        addProperty(msg, name, value.getBytes(ZMQ.CHARSET));
    }

    protected void addProperty(ByteBuffer buf, String name, byte[] value)
    {
        byte[] nameB = name.getBytes(ZMQ.CHARSET);
        int nameLength = nameB.length;
        assert (nameLength <= 255);

        int valueLength = value == null ? 0 : value.length;
        assert (valueLength <= Integer.MAX_VALUE);

        buf.put((byte) nameLength);
        buf.put(nameB);

        Wire.putUInt32(buf, valueLength);
        if (value != null) {
            buf.put(value);
        }
    }

    protected void addProperty(Msg msg, String name, byte[] value)
    {
        byte[] nameB = name.getBytes(ZMQ.CHARSET);
        int nameLength = nameB.length;
        assert (nameLength <= 255);

        int valueLength = value == null ? 0 : value.length;
        assert (valueLength <= Integer.MAX_VALUE);

        msg.put((byte) nameLength);
        msg.put(nameB);

        Wire.putUInt32(msg, valueLength);
        if (value != null) {
            msg.put(value);
        }
    }

    protected int parseMetadata(Msg msg, int offset, boolean zapFlag)
    {
        return parseMetadata(msg.buf(), offset, zapFlag);
    }

    protected int parseMetadata(ByteBuffer msg, int offset, boolean zapFlag)
    {
        ByteBuffer data = msg.duplicate();

        data.position(offset);
        int bytesLeft = data.remaining();
        int index = offset;

        while (bytesLeft > 1) {
            byte nameLength = data.get(index);
            index++;
            bytesLeft -= 1;

            if (bytesLeft < nameLength) {
                break;
            }

            String name = build(data, index, nameLength);
            index += nameLength;
            bytesLeft -= nameLength;

            if (bytesLeft < 4) {
                break;
            }

            int valueLength = Wire.getUInt32(data, index);
            index += 4;
            bytesLeft -= 4;

            if (bytesLeft < valueLength) {
                break;
            }

            String value = build(data, index, valueLength);
            index += valueLength;
            bytesLeft -= valueLength;

            if (IDENTITY.equals(name) && options.recvIdentity) {
                setPeerIdentity(value);
            }
            else if (SOCKET_TYPE.equals(name)) {
                if (!Sockets.compatible(options.type, value)) {
                    return ZError.EINVAL;
                }
            }
            else {
                int rc = property(name, value);
                if (rc == -1) {
                    return -1;
                }
            }
            if (zapFlag) {
                zapProperties.set(name, value);
            }
            else {
                zmtpProperties.set(name, value);
            }
        }
        if (bytesLeft > 0) {
            return ZError.EPROTO;
        }
        return 0;
    }

    private String build(ByteBuffer buf, int offset, int length)
    {
        byte[] bytes = new byte[length];
        int position = buf.position();
        buf.position(offset);
        buf.get(bytes, 0, length);
        buf.position(position);
        return new String(bytes, ZMQ.CHARSET);
    }

    protected int property(String name, String value)
    {
        //  Default implementation does not check
        //  property values and returns 0 to signal success.
        return 0;
    }

    protected String socketType(int socketType)
    {
        return Sockets.name(options.type);
    }

    protected boolean compare(Msg msg, String data, boolean includeLength)
    {
        int start = includeLength ? 1 : 0;
        if (msg.size() < data.length() + start) {
            return false;
        }
        boolean comparison = includeLength ? msg.get(0) == data.length() : true;
        if (comparison) {
            for (int idx = start; idx < data.length(); ++idx) {
                comparison &= (msg.get(idx) == data.charAt(idx - start));
                if (!comparison) {
                    break;
                }
            }
        }
        return comparison;
    }

    protected boolean compare(ByteBuffer a1, byte[] b, int offset, int length)
    {
        if (length > b.length) {
            return false;
        }
        boolean comparison = true;
        for (int idx = 0; idx < length; ++idx) {
            comparison |= a1.get(idx + offset) == b[idx];
            if (!comparison) {
                break;
            }
        }
        return comparison;
    }

    public Msg decode(Msg msg)
    {
        return msg;
    }

    public Msg encode(Msg msg)
    {
        return msg;
    }

    public abstract int zapMsgAvailable();

    public abstract int processHandshakeCommand(Msg msg);

    public abstract int nextHandshakeCommand(Msg msg);

    protected void sendZapRequest(Mechanisms mechanism, boolean more)
    {
        assert (session != null);
        assert (peerAddress != null);
        assert (mechanism != null);

        Msg msg = new Msg();

        //  Address delimiter frame
        msg.setFlags(Msg.MORE);
        boolean rc = session.writeZapMsg(msg);
        assert (rc);

        //  Version frame
        msg = new Msg(3);
        msg.setFlags(Msg.MORE);
        msg.put("1.0".getBytes(ZMQ.CHARSET));
        rc = session.writeZapMsg(msg);
        assert (rc);

        //  Request id frame
        msg = new Msg(1);
        msg.setFlags(Msg.MORE);
        msg.put("1".getBytes(ZMQ.CHARSET));
        rc = session.writeZapMsg(msg);
        assert (rc);

        //  Domain frame
        msg = new Msg(options.zapDomain.length());
        msg.setFlags(Msg.MORE);
        msg.put(options.zapDomain.getBytes(ZMQ.CHARSET));
        rc = session.writeZapMsg(msg);
        assert (rc);

        //  Address frame
        msg = new Msg(peerAddress.address().length());
        msg.setFlags(Msg.MORE);
        msg.put(peerAddress.address().getBytes(ZMQ.CHARSET));
        rc = session.writeZapMsg(msg);
        assert (rc);

        //  Identity frame
        msg = new Msg(options.identitySize);
        msg.setFlags(Msg.MORE);
        msg.put(options.identity, 0, options.identitySize);
        rc = session.writeZapMsg(msg);
        assert (rc);

        //  Mechanism frame
        msg = new Msg(mechanism.name().length());
        msg.put(mechanism.name().getBytes(ZMQ.CHARSET));
        if (more) {
            msg.setFlags(Msg.MORE);
        }
        rc = session.writeZapMsg(msg);
        assert (rc);
    }

    protected int receiveAndProcessZapReply()
    {
        assert (session != null);

        List<Msg> msgs = new ArrayList<>(7); //  ZAP reply consists of 7 frames

        //  Initialize all reply frames
        for (int idx = 0; idx < 7; ++idx) {
            Msg msg = session.readZapMsg();
            if (msg == null) {
                return session.errno.get();
            }
            if ((msg.flags() & Msg.MORE) == (idx < 6 ? 0 : Msg.MORE)) {
                //  Temporary support for security debugging
                puts("NULL I: ZAP handler sent incomplete reply message " + msg);
                return ZError.EPROTO;
            }
            msgs.add(msg);
        }

        //  Address delimiter frame
        if (msgs.get(0).size() > 0) {
            //  Temporary support for security debugging
            puts("NULL I: ZAP handler sent malformed reply message in address delimiter frame " + msgs.get(0));
            return ZError.EPROTO;
        }

        //  Version frame
        if (msgs.get(1).size() != 3 || !compare(msgs.get(1), "1.0", false)) {
            //  Temporary support for security debugging
            puts("NULL I: ZAP handler sent bad version number " + msgs.get(1));
            return ZError.EPROTO;
        }

        //  Request id frame
        if (msgs.get(2).size() != 1 || !compare(msgs.get(2), "1", false)) {
            //  Temporary support for security debugging
            puts("NULL I: ZAP handler sent bad request ID " + msgs.get(2));
            return ZError.EPROTO;
        }

        //  Status code frame
        if (msgs.get(3).size() != 3) {
            //  Temporary support for security debugging
            puts("NULL I: ZAP handler rejected client authentication " + msgs.get(3));
            return ZError.EPROTO;
        }

        //  Save status code
        statusCode = new String(msgs.get(3).data(), ZMQ.CHARSET);

        //  Save user id
        setUserId(msgs.get(5).data());

        //  Process metadata frame
        int rc = parseMetadata(msgs.get(6), 0, true);

        return rc;
    }

    protected final void puts(String msg)
    {
        //  Temporary support for security debugging
        System.out.println(session + " " + msg);
    }

    protected final void appendData(Msg msg, String data)
    {
        msg.put((byte) data.length());
        msg.put(data.getBytes(ZMQ.CHARSET));
    }

    public void destroy()
    {
    }
}
