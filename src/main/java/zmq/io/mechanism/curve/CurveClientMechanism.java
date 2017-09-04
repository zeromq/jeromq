package zmq.io.mechanism.curve;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.mechanism.Mechanism;
import zmq.util.Errno;
import zmq.util.Wire;

public class CurveClientMechanism extends Mechanism
{
    private enum State
    {
        SEND_HELLO,
        EXPECT_WELCOME,
        SEND_INITIATE,
        EXPECT_READY,
        ERROR_RECEIVED,
        CONNECTED
    }

    private State state;

    //  Our public key (C)
    private final byte[] publicKey;
    //  Our secret key (c)
    private final byte[] secretKey;
    //  Our short-term public key (C')
    private final byte[] cnPublic;
    //  Our short-term secret key (c')
    private final byte[] cnSecret;
    //  Server's public key (S)
    private final byte[] serverKey;
    //  Server's short-term public key (S')
    private byte[] cnServer = new byte[Curve.Size.PUBLICKEY.bytes()];
    //  Cookie received from server
    private byte[] cnCookie = new byte[16 + 80];
    //  Intermediary buffer used to speed up boxing and unboxing.
    private final byte[] cnPrecom = new byte[Curve.Size.BEFORENM.bytes()];

    private long cnNonce;
    private long cnPeerNonce;

    private final Curve cryptoBox;

    private final Errno errno;

    public CurveClientMechanism(Options options)
    {
        super(null, null, options);
        this.state = State.SEND_HELLO;
        cnNonce = 1;
        cnPeerNonce = 1;
        publicKey = options.curvePublicKey;
        assert (publicKey != null && publicKey.length == Curve.Size.PUBLICKEY.bytes());
        secretKey = options.curveSecretKey;
        assert (secretKey != null && secretKey.length == Curve.Size.SECRETKEY.bytes());
        serverKey = options.curveServerKey;
        assert (serverKey != null && serverKey.length == Curve.Size.PUBLICKEY.bytes());

        cryptoBox = new Curve();
        //  Generate short-term key pair
        byte[][] keys = cryptoBox.keypair();
        assert (keys != null && keys.length == 2);
        cnPublic = keys[0];
        assert (cnPublic != null && cnPublic.length == Curve.Size.PUBLICKEY.bytes());
        cnSecret = keys[1];
        assert (cnSecret != null && cnSecret.length == Curve.Size.SECRETKEY.bytes());
        errno = options.errno;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        int rc;
        switch (state) {
        case SEND_HELLO:
            rc = produceHello(msg);
            if (rc == 0) {
                state = State.EXPECT_WELCOME;
            }
            break;
        case SEND_INITIATE:
            rc = produceInitiate(msg);
            if (rc == 0) {
                state = State.EXPECT_READY;
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

        int dataSize = msg.size();
        if (dataSize >= 8 && compare(msg, "WELCOME", true)) {
            rc = processWelcome(msg);
        }
        else if (dataSize >= 6 && compare(msg, "READY", true)) {
            rc = processReady(msg);
        }
        else if (dataSize >= 6 && compare(msg, "ERROR", true)) {
            rc = processError(msg);
        }
        else {
            rc = ZError.EPROTO;
        }
        return rc;
    }

    @Override
    public Msg encode(Msg msg)
    {
        assert (state == State.CONNECTED);

        byte flags = 0;
        if (msg.hasMore()) {
            flags |= 0x01;
        }

        ByteBuffer messageNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());

        messageNonce.put("CurveZMQMESSAGEC".getBytes(ZMQ.CHARSET));
        Wire.putUInt64(messageNonce, cnNonce);

        int mlen = Curve.Size.ZERO.bytes() + 1 + msg.size();

        ByteBuffer messagePlaintext = ByteBuffer.allocate(mlen);
        messagePlaintext.put(Curve.Size.ZERO.bytes(), flags);
        messagePlaintext.position(Curve.Size.ZERO.bytes() + 1);
        msg.transfer(messagePlaintext, 0, msg.size());

        ByteBuffer messageBox = ByteBuffer.allocate(mlen);

        int rc = cryptoBox.afternm(messageBox, messagePlaintext, mlen, messageNonce, cnPrecom);
        assert (rc == 0);

        Msg encoded = new Msg(16 + mlen - Curve.Size.BOXZERO.bytes());
        appendData(encoded, "MESSAGE");
        encoded.put(messageNonce, 16, 8);
        encoded.put(messageBox, Curve.Size.BOXZERO.bytes(), mlen - Curve.Size.BOXZERO.bytes());

        cnNonce++;
        return encoded;
    }

    @Override
    public Msg decode(Msg msg)
    {
        assert (state == State.CONNECTED);

        if (msg.size() < 33) {
            //  Temporary support for security debugging
            puts("CURVE I: invalid CURVE server, sent malformed command");
            errno.set(ZError.EPROTO);
            return null;
        }

        if (!compare(msg, "MESSAGE", true)) {
            //  Temporary support for security debugging
            puts("CURVE I: invalid CURVE server, did not send MESSAGE");
            errno.set(ZError.EPROTO);
            return null;
        }

        ByteBuffer messageNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        messageNonce.put("CurveZMQMESSAGES".getBytes(ZMQ.CHARSET));
        msg.transfer(messageNonce, 8, 8);

        long nonce = Wire.getUInt64(msg, 8);

        if (nonce <= cnPeerNonce) {
            errno.set(ZError.EPROTO);
            return null;
        }
        cnPeerNonce = nonce;

        int clen = Curve.Size.BOXZERO.bytes() + msg.size() - 16;

        ByteBuffer messagePlaintext = ByteBuffer.allocate(clen);
        ByteBuffer messageBox = ByteBuffer.allocate(clen);

        messageBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(messageBox, 16, msg.size() - 16);

        int rc = cryptoBox.openAfternm(messagePlaintext, messageBox, clen, messageNonce, cnPrecom);
        if (rc == 0) {
            Msg decoded = new Msg(clen - 1 - Curve.Size.ZERO.bytes());

            byte flags = messagePlaintext.get(Curve.Size.ZERO.bytes());
            if ((flags & 0x01) != 0) {
                decoded.setFlags(Msg.MORE);
            }

            messagePlaintext.position(Curve.Size.ZERO.bytes() + 1);
            decoded.put(messagePlaintext);
            return decoded;
        }
        else {
            //  Temporary support for security debugging
            puts("CURVE I: connection key used for MESSAGE is wrong");
            errno.set(ZError.EPROTO);
            return null;
        }
    }

    @Override
    public Status status()
    {
        if (state == State.CONNECTED) {
            return Status.READY;
        }
        else if (state == State.ERROR_RECEIVED) {
            return Status.ERROR;
        }
        else {
            return Status.HANDSHAKING;
        }
    }

    @Override
    public int zapMsgAvailable()
    {
        return 0;
    }

    private int produceHello(Msg msg)
    {
        ByteBuffer helloNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer helloPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer helloBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        //  Prepare the full nonce
        helloNonce.put("CurveZMQHELLO---".getBytes(ZMQ.CHARSET));
        Wire.putUInt64(helloNonce, cnNonce);

        //  Create Box [64 * %x0](C'->S)
        int rc = cryptoBox.box(helloBox, helloPlaintext, helloPlaintext.capacity(), helloNonce, serverKey, cnSecret);
        if (rc != 0) {
            return -1;
        }
        appendData(msg, "HELLO");
        //  CurveZMQ major and minor version numbers
        msg.put(1);
        msg.put(0);
        //  Anti-amplification padding
        msg.put(new byte[72]);
        //  Client public connection key
        msg.put(cnPublic);
        //  Short nonce, prefixed by "CurveZMQHELLO---"
        msg.put(helloNonce, 16, 8);
        //  Signature, Box [64 * %x0](C'->S)
        msg.put(helloBox, Curve.Size.BOXZERO.bytes(), 80);

        assert (msg.size() == 200);
        cnNonce++;

        return 0;
    }

    private int processWelcome(Msg msg)
    {
        if (msg.size() != 168) {
            //  Temporary support for security debugging
            puts("CURVE I: server HELLO is not correct size");
            return ZError.EPROTO;
        }

        ByteBuffer welcomeNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer welcomePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 128);
        ByteBuffer welcomeBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 144);

        //  Open Box [S' + cookie](C'->S)
        welcomeBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(welcomeBox, 24, 144);

        welcomeNonce.put("WELCOME-".getBytes(ZMQ.CHARSET));
        msg.transfer(welcomeNonce, 8, 16);

        int rc = cryptoBox.open(welcomePlaintext, welcomeBox, welcomeBox.capacity(), welcomeNonce, serverKey, cnSecret);
        if (rc != 0) {
            return ZError.EPROTO;
        }

        welcomePlaintext.position(Curve.Size.ZERO.bytes());
        welcomePlaintext.get(cnServer);
        welcomePlaintext.get(cnCookie);

        //  Message independent precomputation
        rc = cryptoBox.beforenm(cnPrecom, cnServer, cnSecret);
        assert (rc == 0);

        state = State.SEND_INITIATE;

        return 0;
    }

    private int produceInitiate(Msg msg)
    {
        ByteBuffer vouchNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer vouchPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer vouchBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        //  Create vouch = Box [C',S](C->S')
        vouchPlaintext.position(Curve.Size.ZERO.bytes());
        vouchPlaintext.put(cnPublic);
        vouchPlaintext.put(serverKey);

        vouchNonce.put("VOUCH---".getBytes(ZMQ.CHARSET));
        vouchNonce.put(cryptoBox.random(16));

        int rc = cryptoBox.box(vouchBox, vouchPlaintext, vouchPlaintext.capacity(), vouchNonce, cnServer, secretKey);
        if (rc == -1) {
            return -1;
        }

        //  Assume here that metadata is limited to 256 bytes
        ByteBuffer initiateNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer initiatePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 128 + 256);
        ByteBuffer initiateBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 144 + 256);

        //  Create Box [C + vouch + metadata](C'->S')
        initiatePlaintext.position(Curve.Size.ZERO.bytes());
        initiatePlaintext.put(publicKey);
        vouchNonce.limit(16 + 8).position(8);
        initiatePlaintext.put(vouchNonce);
        vouchBox.limit(Curve.Size.BOXZERO.bytes() + 80).position(Curve.Size.BOXZERO.bytes());
        initiatePlaintext.put(vouchBox);

        //  Metadata starts after vouch

        //  Add socket type property
        String socketType = socketType(options.type);
        addProperty(initiatePlaintext, SOCKET_TYPE, socketType);

        //  Add identity property
        if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
            addProperty(initiatePlaintext, IDENTITY, options.identity);
        }

        int mlen = initiatePlaintext.position();

        initiateNonce.put("CurveZMQINITIATE".getBytes(ZMQ.CHARSET));
        Wire.putUInt64(initiateNonce, cnNonce);

        rc = cryptoBox.box(initiateBox, initiatePlaintext, mlen, initiateNonce, cnServer, cnSecret);
        if (rc == -1) {
            return -1;
        }

        appendData(msg, "INITIATE");
        //  Cookie provided by the server in the WELCOME command
        msg.put(cnCookie);
        //  Short nonce, prefixed by "CurveZMQINITIATE"
        msg.put(initiateNonce, 16, 8);
        //  Box [C + vouch + metadata](C'->S')
        msg.put(initiateBox, Curve.Size.BOXZERO.bytes(), mlen - Curve.Size.BOXZERO.bytes());

        assert (msg.size() == 113 + mlen - Curve.Size.BOXZERO.bytes());
        cnNonce++;

        return 0;
    }

    private int processReady(Msg msg)
    {
        if (msg.size() < 30) {
            return ZError.EPROTO;
        }
        int clen = Curve.Size.BOXZERO.bytes() + msg.size() - 14;

        ByteBuffer readyNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer readyPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 256);
        ByteBuffer readyBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 16 + 256);

        readyBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(readyBox, 14, clen - Curve.Size.BOXZERO.bytes());

        readyNonce.put("CurveZMQREADY---".getBytes(ZMQ.CHARSET));
        msg.transfer(readyNonce, 6, 8);
        cnPeerNonce = Wire.getUInt64(msg, 6);

        int rc = cryptoBox.openAfternm(readyPlaintext, readyBox, clen, readyNonce, cnPrecom);
        if (rc != 0) {
            return ZError.EPROTO;
        }

        readyPlaintext.limit(clen);
        rc = parseMetadata(readyPlaintext, Curve.Size.ZERO.bytes(), false);
        if (rc == 0) {
            state = State.CONNECTED;
        }

        return rc;
    }

    private int processError(Msg msg)
    {
        if (state != State.EXPECT_WELCOME && state != State.EXPECT_READY) {
            return ZError.EPROTO;
        }
        if (msg.size() < 7) {
            return ZError.EPROTO;
        }
        byte errorReasonLength = msg.get(6);
        if (errorReasonLength > msg.size() - 7) {
            return ZError.EPROTO;
        }
        state = State.ERROR_RECEIVED;

        return 0;
    }
}
