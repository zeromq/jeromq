package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import zmq.io.Metadata;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.SelectorProviderChooser;
import zmq.msg.MsgAllocator;
import zmq.msg.MsgAllocatorThreshold;
import zmq.poll.PollItem;
import zmq.util.Clock;
import zmq.util.Utils;

public class ZMQ
{
    /******************************************************************************/
    /*  0MQ versioning support.                                                   */
    /******************************************************************************/

    /*  Version macros for compile-time API version detection                     */
    public static final int ZMQ_VERSION_MAJOR = 4;
    public static final int ZMQ_VERSION_MINOR = 1;
    public static final int ZMQ_VERSION_PATCH = 7;

    /*  Context options  */
    public static final int ZMQ_IO_THREADS  = 1;
    public static final int ZMQ_MAX_SOCKETS = 2;

    /*  Default for new contexts                                                  */
    public static final int ZMQ_IO_THREADS_DFLT  = 1;
    public static final int ZMQ_MAX_SOCKETS_DFLT = 1024;

    /******************************************************************************/
    /*  0MQ socket definition.                                                    */
    /******************************************************************************/

    /*  Socket types.                                                             */
    public static final int ZMQ_PAIR    = 0;
    public static final int ZMQ_PUB     = 1;
    public static final int ZMQ_SUB     = 2;
    public static final int ZMQ_REQ     = 3;
    public static final int ZMQ_REP     = 4;
    public static final int ZMQ_DEALER  = 5;
    public static final int ZMQ_ROUTER  = 6;
    public static final int ZMQ_PULL    = 7;
    public static final int ZMQ_PUSH    = 8;
    public static final int ZMQ_XPUB    = 9;
    public static final int ZMQ_XSUB    = 10;
    public static final int ZMQ_STREAM  = 11;
    public static final int ZMQ_SERVER  = 12;
    public static final int ZMQ_CLIENT  = 13;
    public static final int ZMQ_RADIO   = 14;
    public static final int ZMQ_DISH    = 15;
    public static final int ZMQ_CHANNEL = 16;
    public static final int ZMQ_PEER    = 17;
    public static final int ZMQ_RAW     = 18;
    public static final int ZMQ_SCATTER = 19;
    public static final int ZMQ_GATHER  = 20;

    /*  Deprecated aliases                                                        */
    @Deprecated
    public static final int ZMQ_XREQ = ZMQ_DEALER;
    @Deprecated
    public static final int ZMQ_XREP = ZMQ_ROUTER;

    private static final int ZMQ_CUSTOM_OPTION = 1000;

    /*  Socket options.                                                           */
    public static final int ZMQ_AFFINITY            = 4;
    public static final int ZMQ_IDENTITY            = 5;
    public static final int ZMQ_SUBSCRIBE           = 6;
    public static final int ZMQ_UNSUBSCRIBE         = 7;
    public static final int ZMQ_RATE                = 8;
    public static final int ZMQ_RECOVERY_IVL        = 9;
    public static final int ZMQ_SNDBUF              = 11;
    public static final int ZMQ_RCVBUF              = 12;
    public static final int ZMQ_RCVMORE             = 13;
    public static final int ZMQ_FD                  = 14;
    public static final int ZMQ_EVENTS              = 15;
    public static final int ZMQ_TYPE                = 16;
    public static final int ZMQ_LINGER              = 17;
    public static final int ZMQ_RECONNECT_IVL       = 18;
    public static final int ZMQ_BACKLOG             = 19;
    public static final int ZMQ_RECONNECT_IVL_MAX   = 21;
    public static final int ZMQ_MAXMSGSIZE          = 22;
    public static final int ZMQ_SNDHWM              = 23;
    public static final int ZMQ_RCVHWM              = 24;
    public static final int ZMQ_MULTICAST_HOPS      = 25;
    public static final int ZMQ_RCVTIMEO            = 27;
    public static final int ZMQ_SNDTIMEO            = 28;
    public static final int ZMQ_LAST_ENDPOINT       = 32;
    public static final int ZMQ_ROUTER_MANDATORY    = 33;
    public static final int ZMQ_TCP_KEEPALIVE       = 34;
    public static final int ZMQ_TCP_KEEPALIVE_CNT   = 35;
    public static final int ZMQ_TCP_KEEPALIVE_IDLE  = 36;
    public static final int ZMQ_TCP_KEEPALIVE_INTVL = 37;
    public static final int ZMQ_IMMEDIATE           = 39 + ZMQ_CUSTOM_OPTION; // for compatibility with ZMQ_DELAY_ATTACH_ON_CONNECT
    public static final int ZMQ_XPUB_VERBOSE        = 40;
    public static final int ZMQ_ROUTER_RAW          = 41;
    public static final int ZMQ_IPV6                = 42;
    public static final int ZMQ_MECHANISM           = 43;
    public static final int ZMQ_PLAIN_SERVER        = 44;
    public static final int ZMQ_PLAIN_USERNAME      = 45;
    public static final int ZMQ_PLAIN_PASSWORD      = 46;
    public static final int ZMQ_CURVE_SERVER        = 47;
    public static final int ZMQ_CURVE_PUBLICKEY     = 48;
    public static final int ZMQ_CURVE_SECRETKEY     = 49;
    public static final int ZMQ_CURVE_SERVERKEY     = 50;
    public static final int ZMQ_PROBE_ROUTER        = 51;
    public static final int ZMQ_REQ_CORRELATE       = 52;
    public static final int ZMQ_REQ_RELAXED         = 53;
    public static final int ZMQ_CONFLATE            = 54;
    public static final int ZMQ_ZAP_DOMAIN          = 55;
    // TODO: more constants
    public static final int ZMQ_ROUTER_HANDOVER          = 56;
    public static final int ZMQ_TOS                      = 57;
    public static final int ZMQ_CONNECT_RID              = 61;
    public static final int ZMQ_GSSAPI_SERVER            = 62;
    public static final int ZMQ_GSSAPI_PRINCIPAL         = 63;
    public static final int ZMQ_GSSAPI_SERVICE_PRINCIPAL = 64;
    public static final int ZMQ_GSSAPI_PLAINTEXT         = 65;
    public static final int ZMQ_HANDSHAKE_IVL            = 66;
    public static final int ZMQ_SOCKS_PROXY              = 67;
    public static final int ZMQ_XPUB_NODROP              = 69;
    public static final int ZMQ_BLOCKY                   = 70;
    public static final int ZMQ_XPUB_MANUAL              = 71;
    public static final int ZMQ_HEARTBEAT_IVL            = 75;
    public static final int ZMQ_HEARTBEAT_TTL            = 76;
    public static final int ZMQ_HEARTBEAT_TIMEOUT        = 77;
    public static final int ZMQ_XPUB_VERBOSER            = 78;
    @Deprecated
    public static final int ZMQ_XPUB_VERBOSE_UNSUBSCRIBE = 78;
    public static final int ZMQ_HELLO_MSG                = 79;
    public static final int ZMQ_AS_TYPE                  = 80;
    public static final int ZMQ_DISCONNECT_MSG           = 81;
    public static final int ZMQ_HICCUP_MSG               = 82;
    public static final int ZMQ_SELFADDR_PROPERTY_NAME   = 83;

    /* Custom options */
    @Deprecated
    public static final int ZMQ_ENCODER                       = ZMQ_CUSTOM_OPTION + 1;
    @Deprecated
    public static final int ZMQ_DECODER                       = ZMQ_CUSTOM_OPTION + 2;
    public static final int ZMQ_MSG_ALLOCATOR                 = ZMQ_CUSTOM_OPTION + 3;
    public static final int ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD = ZMQ_CUSTOM_OPTION + 4;
    public static final int ZMQ_HEARTBEAT_CONTEXT             = ZMQ_CUSTOM_OPTION + 5;
    public static final int ZMQ_SELECTOR_PROVIDERCHOOSER      = ZMQ_CUSTOM_OPTION + 6;

    /*  Message options                                                           */
    public static final int ZMQ_MORE = 1;

    /*  Send/recv options.                                                        */
    public static final int ZMQ_DONTWAIT = 1;
    public static final int ZMQ_SNDMORE  = 2;

    /*  Deprecated aliases                                                        */
    @Deprecated
    public static final int ZMQ_TCP_ACCEPT_FILTER       = 38;
    @Deprecated
    public static final int ZMQ_IPV4ONLY                = 31;
    @Deprecated
    public static final int ZMQ_DELAY_ATTACH_ON_CONNECT = 39;
    @Deprecated
    public static final int ZMQ_NOBLOCK                 = ZMQ_DONTWAIT;
    @Deprecated
    public static final int ZMQ_FAIL_UNROUTABLE         = ZMQ_ROUTER_MANDATORY;
    @Deprecated
    public static final int ZMQ_ROUTER_BEHAVIOR         = ZMQ_ROUTER_MANDATORY;

    /******************************************************************************/
    /*  0MQ socket events and monitoring                                          */
    /******************************************************************************/

    /*  Socket transport events (tcp and ipc only)                                */
    public static final int ZMQ_EVENT_CONNECTED          = 1;
    public static final int ZMQ_EVENT_CONNECT_DELAYED    = 1 << 1;
    public static final int ZMQ_EVENT_CONNECT_RETRIED    = 1 << 2;
    public static final int ZMQ_EVENT_LISTENING          = 1 << 3;
    public static final int ZMQ_EVENT_BIND_FAILED        = 1 << 4;
    public static final int ZMQ_EVENT_ACCEPTED           = 1 << 5;
    public static final int ZMQ_EVENT_ACCEPT_FAILED      = 1 << 6;
    public static final int ZMQ_EVENT_CLOSED             = 1 << 7;
    public static final int ZMQ_EVENT_CLOSE_FAILED       = 1 << 8;
    public static final int ZMQ_EVENT_DISCONNECTED       = 1 << 9;
    public static final int ZMQ_EVENT_MONITOR_STOPPED    = 1 << 10;
    public static final int ZMQ_EVENT_HANDSHAKE_PROTOCOL = 1 << 15;
    public static final int ZMQ_EVENT_ALL                = 0xffff;
    /*  Unspecified system errors during handshake. Event value is an errno.      */
    public static final int ZMQ_EVENT_HANDSHAKE_FAILED_NO_DETAIL   = 1 << 11;

    /*  Handshake complete successfully with successful authentication (if        *
     *  enabled). Event value is unused.                                          */
    public static final int ZMQ_EVENT_HANDSHAKE_SUCCEEDED           = 1 << 12;

    /*  Protocol errors between ZMTP peers or between server and ZAP handler.     *
     *  Event value is one of ZMQ_PROTOCOL_ERROR_*                                */
    public static final int ZMQ_EVENT_HANDSHAKE_FAILED_PROTOCOL     = 1 << 13;
    /*  Failed authentication requests. Event value is the numeric ZAP status     *
     *  code, i.e. 300, 400 or 500.                                               */
    public static final int ZMQ_EVENT_HANDSHAKE_FAILED_AUTH         = 1 << 14;

    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_UNSPECIFIED                   = 0x10000000;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND            = 0x10000001;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_INVALID_SEQUENCE              = 0x10000002;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_KEY_EXCHANGE                  = 0x10000003;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_UNSPECIFIED = 0x10000011;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_MESSAGE     = 0x10000012;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_HELLO       = 0x10000013;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_INITIATE    = 0x10000014;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_ERROR       = 0x10000015;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_READY       = 0x10000016;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MALFORMED_COMMAND_WELCOME     = 0x10000017;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_INVALID_METADATA              = 0x10000018;
    // the following two may be due to erroneous configuration of a peer
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_CRYPTOGRAPHIC                 = 0x11000001;
    public static final int ZMQ_PROTOCOL_ERROR_ZMTP_MECHANISM_MISMATCH            = 0x11000002;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_UNSPECIFIED                    = 0x20000000;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_MALFORMED_REPLY                = 0x20000001;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_BAD_REQUEST_ID                 = 0x20000002;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_BAD_VERSION                    = 0x20000003;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_INVALID_STATUS_CODE            = 0x20000004;
    public static final int ZMQ_PROTOCOL_ERROR_ZAP_INVALID_METADATA               = 0x20000005;
    public static final int ZMQ_PROTOCOL_ERROR_WS_UNSPECIFIED                     = 0x30000000;

    public static final int ZMQ_POLLIN  = 1;
    public static final int ZMQ_POLLOUT = 2;
    public static final int ZMQ_POLLERR = 4;

    @Deprecated
    public static final int ZMQ_STREAMER  = 1;
    @Deprecated
    public static final int ZMQ_FORWARDER = 2;
    @Deprecated
    public static final int ZMQ_QUEUE     = 3;

    public static final byte[] MESSAGE_SEPARATOR = new byte[0];

    public static final byte[] SUBSCRIPTION_ALL = new byte[0];

    // Android compatibility: not using StandardCharsets (API 19+)
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final byte[] PROXY_PAUSE     = "PAUSE".getBytes(ZMQ.CHARSET);
    public static final byte[] PROXY_RESUME    = "RESUME".getBytes(ZMQ.CHARSET);
    public static final byte[] PROXY_TERMINATE = "TERMINATE".getBytes(ZMQ.CHARSET);

    // Default values for options
    /**
     * Default value for {@link ZMQ#ZMQ_AFFINITY}
     */
    public static final int DEFAULT_AFFINITY = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_SERVER}
     */
    public static final boolean DEFAULT_AS_SERVER = false;
    /**
     * Default value for {@link ZMQ#ZMQ_AS_TYPE}
     */
    public static final int DEFAULT_AS_TYPE = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_BACKLOG}
     */
    public static final int DEFAULT_BACKLOG = 100;
    /**
     * Default value for {@link ZMQ#ZMQ_CONFLATE}
     */
    public static final boolean DEFAULT_CONFLATE = false;
    /**
     * Default value for {@link ZMQ#ZMQ_DISCONNECT_MSG}
     */
    public static final Msg DEFAULT_DISCONNECT_MSG = null;
    /**
     * Default value for {@link ZMQ#ZMQ_GSSAPI_PLAINTEXT}
     */
    public static final boolean DEFAULT_GSS_PLAINTEXT = false;
    /**
     * Default value for {@link ZMQ#ZMQ_HANDSHAKE_IVL}
     */
    public static final int DEFAULT_HANDSHAKE_IVL = 30000;
    /**
     * Default value for {@link ZMQ#ZMQ_HEARTBEAT_CONTEXT}
     */
    public static final byte[] DEFAULT_HEARTBEAT_CONTEXT = new byte[0];
    /**
     * Default value for {@link ZMQ#ZMQ_HEARTBEAT_IVL}
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_HEARTBEAT_TIMEOUT}
     */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_HEARTBEAT_TTL}
     */
    public static final int DEFAULT_HEARTBEAT_TTL = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_HELLO_MSG}
     */
    public static final Msg DEFAULT_HELLO_MSG = null;
    /**
     * Default value for {@link ZMQ#ZMQ_HICCUP_MSG}
     */
    public static final Msg DEFAULT_HICCUP_MSG = null;
    /**
     * Default value for {@link ZMQ#ZMQ_IDENTITY}
     */
    public static final byte[] DEFAULT_IDENTITY = new byte[0];
    /**
     * Default value for {@link ZMQ#ZMQ_IMMEDIATE}
     */
    public static final boolean DEFAULT_IMMEDIATE = true;
    /**
     * Default value for {@link ZMQ#ZMQ_IPV6}
     */
    public static final boolean DEFAULT_IPV6 = ZMQ.PREFER_IPV6;
    /**
     * Default value for {@link ZMQ#ZMQ_LINGER}
     */
    public static final int DEFAULT_LINGER = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_MAXMSGSIZE}
     */
    public static final long DEFAULT_MAX_MSG_SIZE = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_MECHANISM}
     */
    public static final Mechanisms DEFAULT_MECHANISM = Mechanisms.NULL;
    /**
     * Default value for {@link ZMQ#ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD}
     */
    public static final int DEFAULT_ALLOCATION_HEAP_THRESHOLD = Config.MSG_ALLOCATION_HEAP_THRESHOLD.getValue();
    /**
     * Default value for {@link ZMQ#ZMQ_MSG_ALLOCATOR}
     */
    public static final MsgAllocator DEFAULT_MSG_ALLOCATOR = new MsgAllocatorThreshold(DEFAULT_ALLOCATION_HEAP_THRESHOLD);
    /**
     * Default value for {@link ZMQ#ZMQ_RECONNECT_IVL}
     */
    public static final int DEFAULT_RECONNECT_IVL = 100;
    /**
     * Default value for {@link ZMQ#ZMQ_RECONNECT_IVL_MAX}
     */
    public static final int DEFAULT_RECONNECT_IVL_MAX = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_RCVHWM}
     */
    public static final int DEFAULT_RECV_HWM = 1000;
    /**
     * Default value for {@link ZMQ#ZMQ_RCVTIMEO}
     */
    public static final int DEFAULT_RECV_TIMEOUT = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_RCVBUF}
     */
    public static final int DEFAULT_RCVBUF = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_RATE}
     */
    public static final int DEFAULT_RATE = 100;
    /**
     * Default value for {@link ZMQ#ZMQ_RECOVERY_IVL}
     */
    public static final int DEFAULT_RECOVERY_IVL = 10000;
    /**
     * Default value for {@link ZMQ#ZMQ_SELFADDR_PROPERTY_NAME}
     */
    public static final String DEFAULT_SELF_ADDRESS_PROPERTY_NAME = null;
    /**
     * Default value for {@link ZMQ#ZMQ_SNDHWM}
     */
    public static final int DEFAULT_SEND_HWM = 1000;
    /**
     * Default value for {@link ZMQ#ZMQ_SNDTIMEO}
     */
    public static final int DEFAULT_SEND_TIMEOUT = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_SELECTOR_PROVIDERCHOOSER}
     */
    public static final SelectorProviderChooser DEFAULT_SELECTOR_CHOOSER = null;
    /**
     * Default value for {@link ZMQ#ZMQ_SNDBUF}
     */
    public static final int DEFAULT_SNDBUF = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_SOCKS_PROXY}
     */
    public static final String DEFAULT_SOCKS_PROXY_ADDRESS = null;
    /**
     * Default value for {@link ZMQ#ZMQ_TCP_KEEPALIVE}
     */
    public static final int DEFAULT_TCP_KEEP_ALIVE = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_TCP_KEEPALIVE_CNT}
     */
    public static final int DEFAULT_TCP_KEEP_ALIVE_CNT = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_TCP_KEEPALIVE_IDLE}
     */
    public static final int DEFAULT_TCP_KEEP_ALIVE_IDLE = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_TCP_KEEPALIVE_INTVL}
     */
    public static final int DEFAULT_TCP_KEEP_ALIVE_INTVL = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_TOS}
     */
    public static final int DEFAULT_TOS = 0;
    /**
     * Default value for {@link ZMQ#ZMQ_TYPE}
     */
    public static final int DEFAULT_TYPE = -1;
    /**
     * Default value for {@link ZMQ#ZMQ_MULTICAST_HOPS}
     */
    public static final int DEFAULT_MULTICAST_HOPS = 1;
    /**
     * Default value for {@link ZMQ#ZMQ_ZAP_DOMAIN}
     */
    public static final String DEFAULT_ZAP_DOMAIN = "";

    public static final boolean PREFER_IPV6;

    static {
        String preferIPv4Stack = System.getProperty("java.net.preferIPv4Stack");
        String preferIPv6Addresses = System.getProperty("java.net.preferIPv6Addresses");
        PREFER_IPV6 = "false".equalsIgnoreCase(preferIPv4Stack) || "true".equalsIgnoreCase(preferIPv6Addresses);
    }

    /**
     * An interface used to consume events in monitor
     */
    public interface EventConsummer
    {
        void consume(Event ev);

        /**
         * An optional method to close the monitor if needed
         */
        default void close()
        {
            // Default do nothing
        }
    }

    public static class Event
    {
        private static final int VALUE_INTEGER = 1;
        private static final int VALUE_CHANNEL = 2;

        public final int    event;
        public final String addr;
        public final Object arg;
        private final int   flag;

        public Event(int event, String addr, Object arg)
        {
            this.event = event;
            this.addr = addr;
            this.arg = arg;
            if (arg instanceof Integer) {
                flag = VALUE_INTEGER;
            }
            else if (arg instanceof SelectableChannel) {
                flag = VALUE_CHANNEL;
            }
            else {
                flag = 0;
            }
        }

        private Event(int event, String addr, Object arg, int flag)
        {
            this.event = event;
            this.addr = addr;
            this.arg = arg;
            this.flag = flag;
        }

        public boolean write(SocketBase s)
        {
            Msg msg = new Msg(serialize(s.getCtx()));
            return s.send(msg, 0);
        }

        private ByteBuffer serialize(Ctx ctx)
        {
            int size = 4 + 1 + addr.length() + 1; // event + len(addr) + addr + flag
            if (flag == VALUE_INTEGER || flag == VALUE_CHANNEL) {
                size += 4;
            }

            ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
            buffer.putInt(event);
            buffer.put((byte) addr.length());
            buffer.put(addr.getBytes(CHARSET));
            buffer.put((byte) flag);
            if (flag == VALUE_INTEGER) {
                buffer.putInt((Integer) arg);
            }
            else if (flag == VALUE_CHANNEL) {
                int channeldId = ctx.forwardChannel((SelectableChannel) arg);
                buffer.putInt(channeldId);
            }
            buffer.flip();
            return buffer;
        }

        /**
         * Resolve the channel that was associated with this event.
         * Implementation note: to be backward compatible, {@link #arg} only store Integer value, so
         * the channel is resolved using this call.
         * <p>
         * Internally socket are kept using weak values, so it's better to retrieve the channel as early
         * as possible, otherwise it might get lost.
         *
         * @param socket the socket that send the event
         * @return the channel in the event, or null if was not a channel event.
         */
        public SelectableChannel getChannel(SocketBase socket)
        {
            return getChannel(socket.getCtx());
        }

        /**
         * Resolve the channel that was associated with this event.
         * Implementation note: to be backward compatible, {@link #arg} only store Integer value, so
         * the channel is resolved using this call.
         * <p>
         * Internally socket are kept using weak values, so it's better to retrieve the channel as early
         * as possible, otherwise it might get lost.
         *
         * @param ctx the socket that send the event
         * @return the channel in the event, or null if was not a channel event.
         */
        public SelectableChannel getChannel(Ctx ctx)
        {
            if (flag == VALUE_CHANNEL) {
                return ctx.getForwardedChannel((Integer) arg);
            }
            else {
                return null;
            }
        }

        public static Event read(SocketBase s, int flags)
        {
            Msg msg = s.recv(flags);
            if (msg == null) {
                return null;
            }

            ByteBuffer buffer = msg.buf();

            int event = buffer.getInt();
            int len = buffer.get();
            byte[] addr = new byte[len];
            buffer.get(addr);
            int flag = buffer.get();
            Object arg = null;

            if (flag == VALUE_INTEGER || flag == VALUE_CHANNEL) {
                arg = buffer.getInt();
            }

            return new Event(event, new String(addr, CHARSET), arg, flag);
        }

        public static Event read(SocketBase s)
        {
            return read(s, 0);
        }
    }

    //  New context API
    public static Ctx createContext()
    {
        //  Create 0MQ context.
        return new Ctx();
    }

    private static void checkContext(Ctx ctx)
    {
        if (ctx == null || !ctx.isActive()) {
            throw new IllegalStateException();
        }
    }

    private static void destroyContext(Ctx ctx)
    {
        checkContext(ctx);
        ctx.terminate();
    }

    public static void setContextOption(Ctx ctx, int option, int optval)
    {
        checkContext(ctx);
        ctx.set(option, optval);
    }

    public static int getContextOption(Ctx ctx, int option)
    {
        checkContext(ctx);
        return ctx.get(option);
    }

    //  Stable/legacy context API
    public static Ctx init(int ioThreads)
    {
        Utils.checkArgument(ioThreads >= 0, "I/O threads must not be negative");
        Ctx ctx = createContext();
        setContextOption(ctx, ZMQ_IO_THREADS, ioThreads);
        return ctx;
    }

    public static void term(Ctx ctx)
    {
        destroyContext(ctx);
    }

    // Sockets
    public static SocketBase socket(Ctx ctx, int type)
    {
        checkContext(ctx);
        return ctx.createSocket(type);
    }

    private static void checkSocket(SocketBase s)
    {
        if (s == null || !s.isActive()) {
            throw new IllegalStateException();
        }
    }

    public static void closeZeroLinger(SocketBase s)
    {
        checkSocket(s);
        s.setSocketOpt(ZMQ.ZMQ_LINGER, 0);
        s.close();
    }

    public static void close(SocketBase s)
    {
        checkSocket(s);
        s.close();
    }

    public static boolean setSocketOption(SocketBase s, int option, Object optval)
    {
        checkSocket(s);
        return s.setSocketOpt(option, optval);
    }

    public static Object getSocketOptionExt(SocketBase s, int option)
    {
        checkSocket(s);
        return s.getSocketOptx(option);
    }

    public static int getSocketOption(SocketBase s, int opt)
    {
        return s.getSocketOpt(opt);
    }

    public static boolean monitorSocket(SocketBase s, final String addr, int events)
    {
        checkSocket(s);

        return s.monitor(addr, events);
    }

    public static boolean bind(SocketBase s, final String addr)
    {
        checkSocket(s);

        return s.bind(addr);
    }

    public static boolean connect(SocketBase s, String addr)
    {
        checkSocket(s);
        return s.connect(addr);
    }

    public static int connectPeer(SocketBase s, String addr)
    {
        checkSocket(s);
        return s.connectPeer(addr);
    }

    public static boolean disconnectPeer(SocketBase s, int routingId)
    {
        checkSocket(s);
        return s.disconnectPeer(routingId);
    }

    public static boolean unbind(SocketBase s, String addr)
    {
        checkSocket(s);
        return s.termEndpoint(addr);
    }

    public static boolean disconnect(SocketBase s, String addr)
    {
        checkSocket(s);
        return s.termEndpoint(addr);
    }

    // Sending functions.
    public static int send(SocketBase s, String str, int flags)
    {
        byte[] data = str.getBytes(CHARSET);
        return send(s, data, data.length, flags);
    }

    public static int send(SocketBase s, Msg msg, int flags)
    {
        int rc = sendMsg(s, msg, flags);
        if (rc < 0) {
            return -1;
        }

        return rc;
    }

    public static int send(SocketBase s, byte[] buf, int flags)
    {
        return send(s, buf, buf.length, flags);
    }

    public static int send(SocketBase s, byte[] buf, int len, int flags)
    {
        checkSocket(s);

        Msg msg = new Msg(len);
        msg.put(buf, 0, len);

        int rc = sendMsg(s, msg, flags);
        if (rc < 0) {
            return -1;
        }

        return rc;
    }

    // Send multiple messages.
    //
    // If flag bit ZMQ_SNDMORE is set the vector is treated as
    // a single multi-part message, i.e. the last message has
    // ZMQ_SNDMORE bit switched off.
    //
    public int sendiov(SocketBase s, byte[][] a, int count, int flags)
    {
        checkSocket(s);
        int rc = 0;
        Msg msg;

        for (int i = 0; i < count; ++i) {
            msg = new Msg(a[i]);
            if (i == count - 1) {
                flags = flags & ~ZMQ_SNDMORE;
            }
            rc = sendMsg(s, msg, flags);
            if (rc < 0) {
                rc = -1;
                break;
            }
        }
        return rc;

    }

    public static boolean sendMsg(SocketBase socket, byte[]... data)
    {
        int rc;
        if (data.length == 0) {
            return false;
        }
        for (int idx = 0; idx < data.length - 1; ++idx) {
            rc = send(socket, new Msg(data[idx]), ZMQ_MORE);
            if (rc < 0) {
                return false;
            }
        }
        rc = send(socket, new Msg(data[data.length - 1]), 0);
        return rc >= 0;
    }

    public static int sendMsg(SocketBase s, Msg msg, int flags)
    {
        int sz = msgSize(msg);
        boolean rc = s.send(msg, flags);
        if (!rc) {
            return -1;
        }
        return sz;
    }

    // Receiving functions.
    public static Msg recv(SocketBase s, int flags)
    {
        checkSocket(s);
        return recvMsg(s, flags);
    }

    // Receive a multi-part message
    //
    // Receives up to *count_ parts of a multi-part message.
    // Sets *count_ to the actual number of parts read.
    // ZMQ_RCVMORE is set to indicate if a complete multi-part message was read.
    // Returns number of message parts read, or -1 on error.
    //
    // Note: even if -1 is returned, some parts of the message
    // may have been read. Therefore the client must consult
    // *count_ to retrieve message parts successfully read,
    // even if -1 is returned.
    //
    // The iov_base* buffers of each iovec *a_ filled in by this
    // function may be freed using free().
    //
    // Implementation note: We assume zmq::msg_t buffer allocated
    // by zmq::recvmsg can be freed by free().
    // We assume it is safe to steal these buffers by simply
    // not closing the zmq::msg_t.
    //
    public int recviov(SocketBase s, byte[][] a, int count, int flags)
    {
        checkSocket(s);

        int nread = 0;
        boolean recvmore = true;

        for (int i = 0; recvmore && i < count; ++i) {
            // Cheat! We never close any msg
            // because we want to steal the buffer.
            Msg msg = recvMsg(s, flags);
            if (msg == null) {
                nread = -1;
                break;
            }

            // Cheat: acquire zmq_msg buffer.
            a[i] = msg.data();

            // Assume zmq_socket ZMQ_RVCMORE is properly set.
            recvmore = msg.hasMore();
        }
        return nread;
    }

    public static Msg recvMsg(SocketBase s, int flags)
    {
        return s.recv(flags);
    }

    public static boolean join(SocketBase s, String group)
    {
        checkSocket(s);
        return s.join(group);
    }

    public static boolean leave(SocketBase s, String group)
    {
        checkSocket(s);
        return s.leave(group);
    }

    public static Msg msgInit()
    {
        return new Msg();
    }

    public static Msg msgInitWithSize(int messageSize)
    {
        return new Msg(messageSize);
    }

    public static int msgSize(Msg msg)
    {
        return msg.size();
    }

    public static int getMessageOption(Msg msg, int option)
    {
        switch (option) {
        case ZMQ_MORE:
            return msg.hasMore() ? 1 : 0;
        default:
            throw new IllegalArgumentException();
        }
    }

    //  Get message metadata string
    public static String getMessageMetadata(Msg msg, String property)
    {
        String data = null;
        Metadata metadata = msg.getMetadata();
        if (metadata != null) {
            data = metadata.get(property);
        }
        return data;
    }

    //  Set routing id on a message sent over SERVER socket type
    public boolean setMessageRoutingId(Msg msg, int routingId)
    {
        return msg.setRoutingId(routingId);
    }

    //  Get the routing id of a message that came from SERVER socket type
    public int getMessageRoutingId(Msg msg)
    {
        return msg.getRoutingId();
    }

    public boolean setMessageGroup(Msg msg, String group)
    {
        return msg.setGroup(group);
    }

    public String getMessageGroup(Msg msg)
    {
        return msg.getGroup();
    }

    public static void sleep(long seconds)
    {
        sleep(seconds, TimeUnit.SECONDS);
    }

    public static void msleep(long milliseconds)
    {
        sleep(milliseconds, TimeUnit.MILLISECONDS);
    }

    public static void sleep(long amount, TimeUnit unit)
    {
        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(amount, unit));
    }

    /**
     * Polling on items with given selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param selector Open and reuse this selector and do not forget to close when it is not used.
     * @param items
     * @param timeout
     * @return number of events
     */
    public static int poll(Selector selector, PollItem[] items, long timeout)
    {
        return poll(selector, items, items.length, timeout);
    }

    /**
     * Polling on items with given selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param selector Open and reuse this selector and do not forget to close when it is not used.
     * @param items
     * @param count
     * @param timeout
     * @return number of events
     */
    public static int poll(Selector selector, PollItem[] items, int count, long timeout)
    {
        Utils.checkArgument(items != null, "items have to be supplied for polling");
        if (count == 0) {
            if (timeout <= 0) {
                return 0;
            }
            LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(timeout, TimeUnit.MILLISECONDS));
            return 0;
        }
        long now = 0L;
        long end = 0L;

        HashMap<SelectableChannel, SelectionKey> saved = new HashMap<>();
        for (SelectionKey key : selector.keys()) {
            if (key.isValid()) {
                saved.put(key.channel(), key);
            }
        }

        for (int i = 0; i < count; i++) {
            PollItem item = items[i];
            if (item == null) {
                continue;
            }

            SelectableChannel ch = item.getChannel(); // mailbox channel if ZMQ socket
            SelectionKey key = saved.remove(ch);

            if (key != null) {
                if (key.interestOps() != item.interestOps()) {
                    key.interestOps(item.interestOps());
                }
                key.attach(item);
            }
            else {
                try {
                    ch.register(selector, item.interestOps(), item);
                }
                catch (ClosedSelectorException e) {
                    // context was closed asynchronously, exit gracefully
                    return -1;
                }
                catch (ClosedChannelException e) {
                    throw new ZError.IOException(e);
                }
            }
        }

        if (!saved.isEmpty()) {
            for (SelectionKey deprecated : saved.values()) {
                deprecated.cancel();
            }
        }

        boolean firstPass = true;
        int nevents = 0;
        int ready;

        while (true) {
            //  Compute the timeout for the subsequent poll.
            long waitMillis;
            if (firstPass) {
                waitMillis = 0L;
            }
            else if (timeout < 0L) {
                waitMillis = -1L;
            }
            else {
                waitMillis = TimeUnit.NANOSECONDS.toMillis(end - now);
                if (waitMillis == 0) {
                    waitMillis = 1L;
                }
            }

            //  Wait for events.
            try {
                int rc;
                if (waitMillis < 0) {
                    rc = selector.select(0);
                }
                else if (waitMillis == 0) {
                    rc = selector.selectNow();
                }
                else {
                    rc = selector.select(waitMillis);
                }

                for (SelectionKey key : selector.keys()) {
                    PollItem item = (PollItem) key.attachment();
                    ready = item.readyOps(key, rc);
                    if (ready < 0) {
                        return -1;
                    }

                    if (ready > 0) {
                        nevents++;
                    }
                }
                selector.selectedKeys().clear();

            }
            catch (ClosedSelectorException e) {
                // context was closed asynchronously, exit gracefully
                return -1;
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }
            //  If timeout is zero, exit immediately whether there are events or not.
            if (timeout == 0) {
                break;
            }

            if (nevents > 0) {
                break;
            }

            //  At this point we are meant to wait for events but there are none.
            //  If timeout is infinite we can just loop until we get some events.
            if (timeout < 0) {
                if (firstPass) {
                    firstPass = false;
                }
                continue;
            }

            //  The timeout is finite and there are no events. In the first pass
            //  we get a timestamp of when the polling have begun. (We assume that
            //  first pass have taken negligible time). We also compute the time
            //  when the polling should time out.
            if (firstPass) {
                now = Clock.nowNS();
                end = now + TimeUnit.MILLISECONDS.toNanos(timeout);
                if (now == end) {
                    break;
                }
                firstPass = false;
                continue;
            }

            //  Find out whether timeout have expired.
            now = Clock.nowNS();
            if (now >= end) {
                break;
            }
        }
        return nevents;
    }

    //  The proxy functionality
    public static boolean proxy(SocketBase frontend, SocketBase backend, SocketBase capture)
    {
        Utils.checkArgument(frontend != null, "Frontend socket has to be present for proxy");
        Utils.checkArgument(backend != null, "Backend socket has to be present for proxy");
        return Proxy.proxy(frontend, backend, capture, null);
    }

    public static boolean proxy(SocketBase frontend, SocketBase backend, SocketBase capture, SocketBase control)
    {
        Utils.checkArgument(frontend != null, "Frontend socket has to be present for proxy");
        Utils.checkArgument(backend != null, "Backend socket has to be present for proxy");
        return Proxy.proxy(frontend, backend, capture, control);
    }

    public static boolean device(int device, SocketBase frontend, SocketBase backend)
    {
        Utils.checkArgument(frontend != null, "Frontend socket has to be present for proxy");
        Utils.checkArgument(backend != null, "Backend socket has to be present for proxy");
        return Proxy.proxy(frontend, backend, null, null);
    }

    public static long startStopwatch()
    {
        return System.nanoTime();
    }

    public static long stopStopwatch(long watch)
    {
        return (System.nanoTime() - watch) / 1000;
    }

    public static int makeVersion(int major, int minor, int patch)
    {
        return ((major) * 10000 + (minor) * 100 + (patch));
    }

    public static String strerror(int errno)
    {
        return "Errno = " + errno;
    }
}
