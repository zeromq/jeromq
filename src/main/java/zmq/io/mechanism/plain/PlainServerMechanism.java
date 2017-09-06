package zmq.io.mechanism.plain;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;

public class PlainServerMechanism extends Mechanism
{
    private enum State
    {
        WAITING_FOR_HELLO,
        SENDING_WELCOME,
        WAITING_FOR_INITIATE,
        SENDING_READY,
        WAITING_FOR_ZAP_REPLY,
        SENDING_ERROR,
        ERROR_COMMAND_SENT,
        READY
    }

    private State state;

    public PlainServerMechanism(SessionBase session, Address peerAddress, Options options)
    {
        super(session, peerAddress, options);
        this.state = State.WAITING_FOR_HELLO;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        int rc;
        switch (state) {
        case SENDING_WELCOME:
            rc = produceWelcome(msg);
            if (rc == 0) {
                state = State.WAITING_FOR_INITIATE;
            }
            break;
        case SENDING_READY:
            rc = produceReady(msg);
            if (rc == 0) {
                state = State.READY;
            }
            break;
        case SENDING_ERROR:
            rc = produceError(msg);
            if (rc == 0) {
                state = State.ERROR_COMMAND_SENT;
            }
            break;
        default:
            rc = ZError.EAGAIN;
            break;

        }
        return rc;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        int rc;
        switch (state) {
        case WAITING_FOR_HELLO:
            rc = produceHello(msg);
            break;
        case WAITING_FOR_INITIATE:
            rc = produceInitiate(msg);
            break;
        default:
            //  Temporary support for security debugging
            puts("PLAIN Server I: invalid handshake command");
            rc = ZError.EPROTO;
            break;

        }
        return rc;
    }

    @Override
    public Status status()
    {
        if (state == State.READY) {
            return Status.READY;
        }
        else if (state == State.ERROR_COMMAND_SENT) {
            return Status.ERROR;
        }
        else {
            return Status.HANDSHAKING;
        }
    }

    @Override
    public int zapMsgAvailable()
    {
        if (state != State.WAITING_FOR_ZAP_REPLY) {
            return ZError.EFSM;
        }

        int rc = receiveAndProcessZapReply();
        if (rc == 0) {
            state = "200".equals(statusCode) ? State.SENDING_WELCOME : State.SENDING_ERROR;
        }
        return rc;
    }

    private int produceHello(Msg msg)
    {
        int bytesLeft = msg.size();
        int index = 0;
        if (bytesLeft < 6 || !compare(msg, "HELLO", true)) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, did not send HELLO");
            return ZError.EPROTO;
        }
        bytesLeft -= 6;
        index += 6;
        if (bytesLeft < 1) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, did not send username");
            return ZError.EPROTO;
        }
        byte length = msg.get(index);
        bytesLeft -= 1;
        if (bytesLeft < length) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, sent malformed username");
            return ZError.EPROTO;
        }
        byte[] tmp = new byte[length];
        index += 1;
        msg.getBytes(index, tmp, 0, length);
        byte[] username = tmp;
        bytesLeft -= length;
        index += length;

        length = msg.get(index);
        bytesLeft -= 1;
        if (bytesLeft < length) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, sent malformed password");
            return ZError.EPROTO;
        }
        tmp = new byte[length];
        index += 1;
        msg.getBytes(index, tmp, 0, length);
        byte[] password = tmp;
        bytesLeft -= length;
        //        index += length;

        if (bytesLeft > 0) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, sent extraneous data");
            return ZError.EPROTO;
        }

        //  Use ZAP protocol (RFC 27) to authenticate the user.
        int rc = session.zapConnect();
        if (rc == 0) {
            sendZapRequest(username, password);
            rc = receiveAndProcessZapReply();
            if (rc == 0) {
                state = "200".equals(statusCode) ? State.SENDING_WELCOME : State.SENDING_ERROR;
            }
            else if (rc == ZError.EAGAIN) {
                state = State.WAITING_FOR_ZAP_REPLY;
            }
            else {
                return -1;
            }
        }
        else {
            state = State.SENDING_WELCOME;
        }

        return 0;
    }

    private int produceWelcome(Msg msg)
    {
        appendData(msg, "WELCOME");
        return 0;
    }

    private int produceInitiate(Msg msg)
    {
        int bytesLeft = msg.size();
        if (bytesLeft < 9 || !compare(msg, "INITIATE", true)) {
            //  Temporary support for security debugging
            puts("PLAIN I: invalid PLAIN client, did not send INITIATE");
            return ZError.EPROTO;
        }

        int rc = parseMetadata(msg, 9, false);
        if (rc == 0) {
            state = State.SENDING_READY;
        }
        return rc;
    }

    private int produceReady(Msg msg)
    {
        //  Add command name
        appendData(msg, "READY");

        //  Add socket type property
        String socketType = socketType(options.type);
        addProperty(msg, SOCKET_TYPE, socketType);

        //  Add identity property
        if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
            addProperty(msg, IDENTITY, options.identity);
        }

        return 0;
    }

    private int produceError(Msg msg)
    {
        assert (statusCode != null && statusCode.length() == 3);

        appendData(msg, "ERROR");
        appendData(msg, statusCode);

        return 0;
    }

    private void sendZapRequest(byte[] username, byte[] password)
    {
        sendZapRequest(Mechanisms.PLAIN, true);

        //  Username frame
        Msg msg = new Msg(username.length);
        msg.setFlags(Msg.MORE);
        msg.put(username);
        boolean rc = session.writeZapMsg(msg);
        assert (rc);

        //  Password frame
        msg = new Msg(password.length);
        msg.put(password);
        rc = session.writeZapMsg(msg);
        assert (rc);
    }
}
