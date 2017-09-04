package zmq.io.mechanism.plain;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.mechanism.Mechanism;

public class PlainClientMechanism extends Mechanism
{
    private enum State
    {
        SENDING_HELLO,
        WAITING_FOR_WELCOME,
        SENDING_INITIATE,
        WAITING_FOR_READY,
        ERROR_COMMAND_RECEIVED,
        READY
    }

    private State state;

    public PlainClientMechanism(Options options)
    {
        super(null, null, options);
        this.state = State.SENDING_HELLO;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        int rc;
        switch (state) {
        case SENDING_HELLO:
            rc = produceHello(msg);
            if (rc == 0) {
                state = State.WAITING_FOR_WELCOME;
            }
            break;
        case SENDING_INITIATE:
            rc = produceInitiate(msg);
            if (rc == 0) {
                state = State.WAITING_FOR_READY;
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
            //  Temporary support for security debugging
            System.out.println("PLAIN Client I: invalid handshake command");
            rc = ZError.EPROTO;
        }
        return rc;
    }

    @Override
    public Status status()
    {
        if (state == State.READY) {
            return Status.READY;
        }
        else if (state == State.ERROR_COMMAND_RECEIVED) {
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
        String plainUsername = options.plainUsername;
        assert (plainUsername.length() < 256);

        String plainPassword = options.plainPassword;
        assert (plainPassword.length() < 256);

        appendData(msg, "HELLO");
        appendData(msg, plainUsername);
        appendData(msg, plainPassword);

        return 0;
    }

    private int processWelcome(Msg msg)
    {
        if (state != State.WAITING_FOR_WELCOME) {
            return ZError.EPROTO;
        }
        if (msg.size() != 8) {
            return ZError.EPROTO;
        }
        state = State.SENDING_INITIATE;
        return 0;
    }

    private int produceInitiate(Msg msg)
    {
        //  Add mechanism string
        appendData(msg, "INITIATE");

        //  Add socket type property
        String socketType = socketType(options.type);
        addProperty(msg, SOCKET_TYPE, socketType);

        //  Add identity property
        if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
            addProperty(msg, IDENTITY, options.identity);
        }

        return 0;
    }

    private int processReady(Msg msg)
    {
        if (state != State.WAITING_FOR_READY) {
            return ZError.EPROTO;
        }
        int rc = parseMetadata(msg, 6, false);
        if (rc == 0) {
            state = State.READY;
        }

        return rc;
    }

    private int processError(Msg msg)
    {
        if (state != State.WAITING_FOR_WELCOME && state != State.WAITING_FOR_READY) {
            return ZError.EPROTO;
        }
        if (msg.size() < 7) {
            return ZError.EPROTO;
        }
        byte errorReasonLength = msg.get(6);
        if (errorReasonLength > msg.size() - 7) {
            return ZError.EPROTO;
        }
        state = State.ERROR_COMMAND_RECEIVED;

        return 0;
    }
}
