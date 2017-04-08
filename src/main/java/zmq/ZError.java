package zmq;

import java.net.SocketException;
import java.nio.channels.ClosedChannelException;

public class ZError
{
    private ZError()
    {
    }

    public static class CtxTerminatedException extends RuntimeException
    {
        private static final long serialVersionUID = -4404921838608052956L;

        public CtxTerminatedException()
        {
            super();
        }
    }

    public static class InstantiationException extends RuntimeException
    {
        private static final long serialVersionUID = -4404921838608052955L;

        public InstantiationException(Throwable cause)
        {
            super(cause);
        }
    }

    public static class IOException extends RuntimeException
    {
        private static final long serialVersionUID = 9202470691157986262L;

        public IOException(java.io.IOException e)
        {
            super(e);
        }
    }

    public static final int ENOENT          = 2;
    public static final int EINTR           = 4;
    public static final int EACCESS         = 13;
    public static final int EFAULT          = 14;
    public static final int EINVAL          = 22;
    public static final int EAGAIN          = 35;
    public static final int EINPROGRESS     = 36;
    public static final int EPROTONOSUPPORT = 43;
    public static final int ENOTSUP         = 45;
    public static final int EADDRINUSE      = 48;
    public static final int EADDRNOTAVAIL   = 49;
    public static final int ENETDOWN        = 50;
    public static final int ENOBUFS         = 55;
    public static final int EISCONN         = 56;
    public static final int ENOTCONN        = 57;
    public static final int ECONNREFUSED    = 61;
    public static final int EHOSTUNREACH    = 65;

    private static final int ZMQ_HAUSNUMERO = 156384712;

    public static final int ENOTSOCK     = ZMQ_HAUSNUMERO + 5;
    public static final int EMSGSIZE     = ZMQ_HAUSNUMERO + 10;
    public static final int EAFNOSUPPORT = ZMQ_HAUSNUMERO + 11;
    public static final int ENETUNREACH  = ZMQ_HAUSNUMERO + 12;

    public static final int ECONNABORTED = ZMQ_HAUSNUMERO + 13;
    public static final int ECONNRESET   = ZMQ_HAUSNUMERO + 14;
    public static final int ETIMEDOUT    = ZMQ_HAUSNUMERO + 16;
    public static final int ENETRESET    = ZMQ_HAUSNUMERO + 18;

    public static final int EFSM           = ZMQ_HAUSNUMERO + 51;
    public static final int ENOCOMPATPROTO = ZMQ_HAUSNUMERO + 52;
    public static final int ETERM          = ZMQ_HAUSNUMERO + 53;
    public static final int EMTHREAD       = ZMQ_HAUSNUMERO + 54;

    public static final int EIOEXC  = ZMQ_HAUSNUMERO + 105;
    public static final int ESOCKET = ZMQ_HAUSNUMERO + 106;
    public static final int EMFILE  = ZMQ_HAUSNUMERO + 107;

    public static final int EPROTO = ZMQ_HAUSNUMERO + 108;

    public static int exccode(java.io.IOException e)
    {
        if (e instanceof SocketException) {
            return ESOCKET;
        }
        else if (e instanceof ClosedChannelException) {
            return ENOTCONN;
        }
        else {
            return EIOEXC;
        }
    }

    public static String toString(int code)
    {
        switch (code) {
        case EADDRINUSE:
            return "Address already in use";
        case EFSM:
            return "Operation cannot be accomplished in current state";
        case ENOCOMPATPROTO:
            return "The protocol is not compatible with the socket type";
        case ETERM:
            return "Context was terminated";
        case EMTHREAD:
            return "No thread available";
        }
        return "";
    }
}
