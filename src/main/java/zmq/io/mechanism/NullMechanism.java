package zmq.io.mechanism;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.net.Address;

class NullMechanism extends Mechanism
{
    private static final String OK    = "200";
    private static final String READY = "READY";
    private static final String ERROR = "ERROR";

    private boolean readyCommandSent;
    private boolean errorCommandSent;

    private boolean readyCommandReceived;
    private boolean errorCommandReceived;

    private boolean zapConnected;
    private boolean zapRequestSent;
    private boolean zapReplyReceived;

    NullMechanism(SessionBase session, Address peerAddress, Options options)
    {
        super(session, peerAddress, options);

        //  NULL mechanism only uses ZAP if there's a domain defined
        //  This prevents ZAP requests on naive sockets
        if (options.zapDomain != null && options.zapDomain.length() > 0 && session.zapConnect() == 0) {
            zapConnected = true;
        }
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        if (readyCommandSent || errorCommandSent) {
            return ZError.EAGAIN;
        }

        if (zapConnected && !zapReplyReceived) {
            if (zapRequestSent) {
                return ZError.EAGAIN;
            }

            sendZapRequest(Mechanisms.NULL, false);
            zapRequestSent = true;

            int rc = receiveAndProcessZapReply();
            if (rc != 0) {
                return rc;
            }
            zapReplyReceived = true;
        }

        if (zapReplyReceived && !OK.equals(statusCode)) {
            appendData(msg, ERROR);
            appendData(msg, statusCode);

            errorCommandSent = true;
            return 0;
        }

        //  Add mechanism string
        appendData(msg, READY);

        //  Add socket type property
        String socketType = socketType(options.type);
        addProperty(msg, SOCKET_TYPE, socketType);

        //  Add identity property
        if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
            addProperty(msg, IDENTITY, options.identity);
        }
        readyCommandSent = true;

        return 0;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        if (readyCommandReceived || errorCommandReceived) {
            puts("NULL I: client sent invalid NULL handshake (duplicate READY)");
            return ZError.EPROTO;
        }
        int dataSize = msg.size();

        int rc;
        if (dataSize >= 6 && compare(msg, READY, true)) {
            rc = processReadyCommand(msg);
        }
        else if (dataSize >= 6 && compare(msg, ERROR, true)) {
            rc = processErrorCommand(msg);
        }
        else {
            puts("NULL I: client sent invalid NULL handshake (not READY) ");
            return ZError.EPROTO;
        }
        return rc;
    }

    private int processReadyCommand(Msg msg)
    {
        readyCommandReceived = true;
        return parseMetadata(msg, 6, false);
    }

    private int processErrorCommand(Msg msg)
    {
        if (msg.size() < 7) {
            return ZError.EPROTO;
        }
        byte errorReasonLength = msg.get(6);
        if (errorReasonLength > msg.size() - 7) {
            return ZError.EPROTO;
        }
        errorCommandReceived = true;
        return 0;
    }

    @Override
    public int zapMsgAvailable()
    {
        if (zapReplyReceived) {
            return ZError.EFSM;
        }
        int rc = receiveAndProcessZapReply();
        if (rc == 0) {
            zapReplyReceived = true;
        }

        return rc;
    }

    @Override
    public Status status()
    {
        boolean commandSent = readyCommandSent || errorCommandSent;
        boolean commandReceived = readyCommandReceived || errorCommandReceived;

        if (readyCommandSent && readyCommandReceived) {
            return Status.READY;
        }
        if (commandSent && commandReceived) {
            return Status.ERROR;
        }
        return Status.HANDSHAKING;
    }
}
