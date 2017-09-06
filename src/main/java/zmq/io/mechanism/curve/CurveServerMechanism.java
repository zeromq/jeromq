package zmq.io.mechanism.curve;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;
import zmq.util.Errno;
import zmq.util.Wire;

public class CurveServerMechanism extends Mechanism
{
    private enum State
    {
        EXPECT_HELLO,
        SEND_WELCOME,
        EXPECT_INITIATE,
        EXPECT_ZAP_REPLY,
        SEND_READY,
        SEND_ERROR,
        ERROR_SENT,
        CONNECTED
    }

    private long cnNonce;
    private long cnPeerNonce;
    //  Our secret key (s)
    private final byte[] secretKey;
    //  Our short-term public key (S')
    private final byte[] cnPublic;
    //  Our short-term secret key (s')
    private final byte[] cnSecret;
    //  Client's short-term public key (C')
    private byte[] cnClient = new byte[Curve.Size.PUBLICKEY.bytes()];
    //  Key used to produce cookie
    private byte[] cookieKey;
    //  Intermediary buffer used to speed up boxing and unboxing.
    private final byte[] cnPrecom = new byte[Curve.Size.BEFORENM.bytes()];

    private State state;

    private final Curve cryptoBox;

    private final Errno errno;

    public CurveServerMechanism(SessionBase session, Address peerAddress, Options options)
    {
        super(session, peerAddress, options);
        this.state = State.EXPECT_HELLO;
        cnNonce = 1;
        cnPeerNonce = 1;

        secretKey = options.curveSecretKey;
        assert (secretKey != null && secretKey.length == Curve.Size.SECRETKEY.bytes());
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
        case SEND_WELCOME:
            rc = produceWelcome(msg);
            if (rc == 0) {
                state = State.EXPECT_INITIATE;
            }
            break;
        case SEND_READY:
            rc = produceReady(msg);
            if (rc == 0) {
                state = State.CONNECTED;
            }
            break;
        case SEND_ERROR:
            rc = produceError(msg);
            if (rc == 0) {
                state = State.ERROR_SENT;
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
        case EXPECT_HELLO:
            rc = processHello(msg);
            break;
        case EXPECT_INITIATE:
            rc = processInitiate(msg);
            break;
        default:
            //  Temporary support for security debugging
            puts("CURVE I: invalid handshake command");
            rc = ZError.EPROTO;
            break;

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
        messageNonce.put("CurveZMQMESSAGES".getBytes(ZMQ.CHARSET));
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
            puts("CURVE I: invalid CURVE client, sent malformed command");
            errno.set(ZError.EPROTO);
            return null;
        }

        if (!compare(msg, "MESSAGE", true)) {
            //  Temporary support for security debugging
            puts("CURVE I: invalid CURVE client, did not send MESSAGE");
            errno.set(ZError.EPROTO);
            return null;
        }

        ByteBuffer messageNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        messageNonce.put("CurveZMQMESSAGEC".getBytes(ZMQ.CHARSET));
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
    public int zapMsgAvailable()
    {
        if (state != State.EXPECT_ZAP_REPLY) {
            return ZError.EFSM;
        }

        int rc = receiveAndProcessZapReply();
        if (rc == 0) {
            state = "200".equals(statusCode) ? State.SEND_READY : State.SEND_ERROR;
        }
        return rc;
    }

    @Override
    public Status status()
    {
        if (state == State.CONNECTED) {
            return Status.READY;
        }
        else if (state == State.ERROR_SENT) {
            return Status.ERROR;
        }
        else {
            return Status.HANDSHAKING;
        }
    }

    private int processHello(Msg msg)
    {
        if (msg.size() != 200) {
            //  Temporary support for security debugging
            puts("CURVE I: client HELLO is not correct size");
            return ZError.EPROTO;
        }

        if (!compare(msg, "HELLO", true)) {
            //  Temporary support for security debugging
            puts("CURVE I: client HELLO has invalid command name");
            return ZError.EPROTO;
        }

        byte major = msg.get(6);
        byte minor = msg.get(7);

        if (major != 1 || minor != 0) {
            //  Temporary support for security debugging
            puts("CURVE I: client HELLO has unknown version number");
            return ZError.EPROTO;
        }

        //  Save client's short-term public key (C')
        msg.getBytes(80, cnClient, 0, Curve.Size.PUBLICKEY.bytes());

        ByteBuffer helloNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer helloPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer helloBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        helloNonce.put("CurveZMQHELLO---".getBytes(ZMQ.CHARSET));
        msg.transfer(helloNonce, 112, 8);
        cnPeerNonce = Wire.getUInt64(msg, 112);

        helloBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(helloBox, 120, 80);

        //  Open Box [64 * %x0](C'->S)
        int rc = cryptoBox.open(helloPlaintext, helloBox, helloBox.capacity(), helloNonce, cnClient, secretKey);
        if (rc != 0) {
            //  Temporary support for security debugging
            puts("CURVE I: cannot open client HELLO -- wrong server key?");
            return ZError.EPROTO;
        }

        state = State.SEND_WELCOME;
        return 0;
    }

    private int produceWelcome(Msg msg)
    {
        ByteBuffer cookieNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer cookiePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer cookieCiphertext = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        //  Create full nonce for encryption
        //  8-byte prefix plus 16-byte random nonce
        cookieNonce.put("COOKIE--".getBytes(ZMQ.CHARSET));
        cookieNonce.put(cryptoBox.random(16));

        //  Generate cookie = Box [C' + s'](t)
        cookiePlaintext.position(Curve.Size.ZERO.bytes());
        cookiePlaintext.put(cnClient);
        cookiePlaintext.put(cnSecret);

        //  Generate fresh cookie key
        cookieKey = cryptoBox.random(Curve.Size.KEY.bytes());

        //  Encrypt using symmetric cookie key
        int rc = cryptoBox
                .secretbox(cookieCiphertext, cookiePlaintext, cookiePlaintext.capacity(), cookieNonce, cookieKey);
        assert (rc == 0);

        ByteBuffer welcomeNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer welcomePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 128);
        ByteBuffer welcomeCiphertext = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 144);

        //  Create full nonce for encryption
        //  8-byte prefix plus 16-byte random nonce
        welcomeNonce.put("WELCOME-".getBytes(ZMQ.CHARSET));
        welcomeNonce.put(cryptoBox.random(Curve.Size.NONCE.bytes() - 8));

        //  Create 144-byte Box [S' + cookie](S->C')
        welcomePlaintext.position(Curve.Size.ZERO.bytes());
        welcomePlaintext.put(cnPublic);
        cookieNonce.limit(16 + 8).position(8);
        welcomePlaintext.put(cookieNonce);
        cookieCiphertext.limit(Curve.Size.BOXZERO.bytes() + 80).position(Curve.Size.BOXZERO.bytes());
        welcomePlaintext.put(cookieCiphertext);

        rc = cryptoBox.box(
                           welcomeCiphertext,
                           welcomePlaintext,
                           welcomePlaintext.capacity(),
                           welcomeNonce,
                           cnClient,
                           secretKey);
        if (rc == -1) {
            return -1;
        }
        appendData(msg, "WELCOME");
        msg.put(welcomeNonce, 8, 16);
        msg.put(welcomeCiphertext, Curve.Size.BOXZERO.bytes(), 144);

        assert (msg.size() == 168);
        return 0;
    }

    private int processInitiate(Msg msg)
    {
        if (msg.size() < 257) {
            //  Temporary support for security debugging
            puts("CURVE I: client INITIATE is not correct size");
            return ZError.EPROTO;
        }

        if (!compare(msg, "INITIATE", true)) {
            //  Temporary support for security debugging
            puts("CURVE I: client INITIATE has invalid command name");
            return ZError.EPROTO;
        }
        ByteBuffer cookieNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer cookiePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer cookieBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        //  Open Box [C' + s'](t)
        cookieBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(cookieBox, 25, 80);

        cookieNonce.put("COOKIE--".getBytes(ZMQ.CHARSET));
        msg.transfer(cookieNonce, 9, 16);

        int rc = cryptoBox.secretboxOpen(cookiePlaintext, cookieBox, cookieBox.capacity(), cookieNonce, cookieKey);
        if (rc != 0) {
            //  Temporary support for security debugging
            puts("CURVE I: cannot open client INITIATE cookie");
            return ZError.EPROTO;
        }

        //  Check cookie plain text is as expected [C' + s']
        if (!compare(cookiePlaintext, cnClient, Curve.Size.ZERO.bytes(), 32)
                || !compare(cookiePlaintext, cnSecret, Curve.Size.ZERO.bytes() + 32, 32)) {
            //  Temporary support for security debugging
            puts("CURVE I: client INITIATE cookie is not valid");
            return ZError.EPROTO;
        }

        int clen = msg.size() - 113 + Curve.Size.BOXZERO.bytes();

        ByteBuffer initiateNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer initiatePlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 128 + 256);
        ByteBuffer initiateBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 144 + 256);

        //  Open Box [C + vouch + metadata](C'->S')
        initiateBox.position(Curve.Size.BOXZERO.bytes());
        msg.transfer(initiateBox, 113, clen - Curve.Size.BOXZERO.bytes());

        initiateNonce.put("CurveZMQINITIATE".getBytes(ZMQ.CHARSET));
        msg.transfer(initiateNonce, 105, 8);

        cnPeerNonce = Wire.getUInt64(msg, 105);

        rc = cryptoBox.open(initiatePlaintext, initiateBox, clen, initiateNonce, cnClient, cnSecret);
        if (rc != 0) {
            //  Temporary support for security debugging
            puts("CURVE I: cannot open client INITIATE");
            return ZError.EPROTO;
        }

        byte[] clientKey = new byte[128 + 256];
        initiatePlaintext.position(Curve.Size.ZERO.bytes());
        initiatePlaintext.get(clientKey);

        ByteBuffer vouchNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer vouchPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 64);
        ByteBuffer vouchBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 80);

        //  Open Box Box [C',S](C->S') and check contents
        vouchBox.position(Curve.Size.BOXZERO.bytes());
        initiatePlaintext.limit(Curve.Size.ZERO.bytes() + 48 + 80).position(Curve.Size.ZERO.bytes() + 48);
        vouchBox.put(initiatePlaintext);

        vouchNonce.put("VOUCH---".getBytes(ZMQ.CHARSET));
        initiatePlaintext.limit(Curve.Size.ZERO.bytes() + 32 + 16).position(Curve.Size.ZERO.bytes() + 32);
        vouchNonce.put(initiatePlaintext);

        rc = cryptoBox.open(vouchPlaintext, vouchBox, vouchBox.capacity(), vouchNonce, clientKey, cnSecret);
        if (rc != 0) {
            //  Temporary support for security debugging
            puts("CURVE I: cannot open client INITIATE vouch");
            return ZError.EPROTO;
        }

        //  What we decrypted must be the client's short-term public key
        if (!compare(vouchPlaintext, cnClient, Curve.Size.ZERO.bytes(), 32)) {
            //  Temporary support for security debugging
            puts("CURVE I: invalid handshake from client (public key)");
            return ZError.EPROTO;
        }

        //  Precompute connection secret from client key
        rc = cryptoBox.beforenm(cnPrecom, cnClient, cnSecret);
        assert (rc == 0);

        //  Use ZAP protocol (RFC 27) to authenticate the user.
        rc = session.zapConnect();
        if (rc == 0) {
            sendZapRequest(clientKey);
            rc = receiveAndProcessZapReply();
            if (rc == 0) {
                state = "200".equals(statusCode) ? State.SEND_READY : State.SEND_ERROR;
            }
            else if (rc == ZError.EAGAIN) {
                state = State.EXPECT_ZAP_REPLY;
            }
            else {
                return -1;
            }
        }
        else {
            state = State.SEND_READY;
        }
        initiatePlaintext.position(0);
        initiatePlaintext.limit(clen);
        return parseMetadata(initiatePlaintext, Curve.Size.ZERO.bytes() + 128, false);
    }

    private int produceReady(Msg msg)
    {
        ByteBuffer readyNonce = ByteBuffer.allocate(Curve.Size.NONCE.bytes());
        ByteBuffer readyPlaintext = ByteBuffer.allocate(Curve.Size.ZERO.bytes() + 256);
        ByteBuffer readyBox = ByteBuffer.allocate(Curve.Size.BOXZERO.bytes() + 16 + 256);

        //  Create Box [metadata](S'->C')
        readyPlaintext.position(Curve.Size.ZERO.bytes());
        //  Add socket type property
        String socketType = socketType(options.type);
        addProperty(readyPlaintext, SOCKET_TYPE, socketType);

        //  Add identity property
        if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
            addProperty(readyPlaintext, IDENTITY, options.identity);
        }

        int mlen = readyPlaintext.position();
        readyNonce.put("CurveZMQREADY---".getBytes(ZMQ.CHARSET));
        Wire.putUInt64(readyNonce, cnNonce);

        int rc = cryptoBox.afternm(readyBox, readyPlaintext, mlen, readyNonce, cnPrecom);
        assert (rc == 0);

        appendData(msg, "READY");
        //  Short nonce, prefixed by "CurveZMQREADY---"
        msg.put(readyNonce, 16, 8);
        //  Box [metadata](S'->C')
        msg.put(readyBox, Curve.Size.BOXZERO.bytes(), mlen - Curve.Size.BOXZERO.bytes());

        assert (msg.size() == 14 + mlen - Curve.Size.BOXZERO.bytes());
        cnNonce++;

        return 0;
    }

    private int produceError(Msg msg)
    {
        assert (statusCode != null && statusCode.length() == 3);

        appendData(msg, "ERROR");
        appendData(msg, statusCode);

        return 0;
    }

    private void sendZapRequest(byte[] key)
    {
        sendZapRequest(Mechanisms.CURVE, true);

        //  Credentials frame
        Msg msg = new Msg(Curve.Size.PUBLICKEY.bytes());
        msg.put(key, 0, Curve.Size.PUBLICKEY.bytes());
        boolean rc = session.writeZapMsg(msg);
        assert (rc);
    }
}
