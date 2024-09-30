package org.zeromq;

import java.io.Closeable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.zeromq.proto.ZPicture;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZError.CtxTerminatedException;
import zmq.io.coder.IDecoder;
import zmq.io.coder.IEncoder;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.SelectorProviderChooser;
import zmq.msg.MsgAllocator;
import zmq.util.Draft;
import zmq.util.Z85;
import zmq.util.function.BiFunction;
import zmq.util.function.Consumer;

/**
 * <p>The ØMQ lightweight messaging kernel is a library which extends the standard socket interfaces
 * with features traditionally provided by specialised messaging middleware products.
 * ØMQ sockets provide an abstraction of asynchronous message queues, multiple messaging patterns,
 * message filtering (subscriptions), seamless access to multiple transport protocols and more.</p>
 *
 * <p>Following is an overview of ØMQ concepts, describes how ØMQ abstracts standard sockets
 * and provides a reference manual for the functions provided by the ØMQ library.</p>
 *
 * <h2>Contexts</h2>
 * <p>Before using any ØMQ library functions you must create a {@link ZMQ.Context ØMQ context} using {@link ZMQ#context(int)}.
 * When you exit your application you must destroy the context using {@link ZMQ.Context#close()}.</p>
 *
 * <h3>Thread safety</h3>
 * A ØMQ context is thread safe and may be shared among as many application threads as necessary,
 * without any additional locking required on the part of the caller.
 * <br>
 * Individual ØMQ sockets are not thread safe except in the case
 * where full memory barriers are issued when migrating a socket from one thread to another.
 * <br>
 * In practice this means applications can create a socket in one thread with * {@link ZMQ.Context#socket(SocketType)}
 * and then pass it to a newly created thread as part of thread initialization.
 * <p>
 * <h3>Multiple contexts</h3>
 * Multiple contexts may coexist within a single application.
 * <br>
 * Thus, an application can use ØMQ directly and at the same time make use of any number of additional libraries
 * or components which themselves make use of ØMQ as long as the above guidelines regarding thread safety are adhered to.
 * <p>
 * <h2>Messages</h2>
 * A ØMQ message is a discrete unit of data passed between applications or components of the same application.
 * ØMQ messages have no internal structure and from the point of view of ØMQ itself
 * they are considered to be opaque binary data.
 * <p>
 * <h2>Sockets</h2>
 * {@link ZMQ.Socket ØMQ sockets} present an abstraction of a asynchronous message queue,
 * with the exact queueing semantics depending on the socket type in use.
 * <p>
 * <h2>Transports</h2>
 * <p>A ØMQ socket can use multiple different underlying transport mechanisms.
 * Each transport mechanism is suited to a particular purpose and has its own advantages and drawbacks.</p>
 *
 * <p>The following transport mechanisms are provided:</p>
 * <ul>
 * <li>Unicast transport using TCP</li>
 * <li>Local inter-process communication transport</li>
 * <li>Local in-process (inter-thread) communication transport</li>
 * </ul>
 *
 * <h2>Proxies</h2>
 * <p>ØMQ provides proxies to create fanout and fan-in topologies.
 * A proxy connects a frontend socket to a backend socket
 * and switches all messages between the two sockets, opaquely.
 * A proxy may optionally capture all traffic to a third socket.</p>
 *
 * <h2>Security</h2>
 * <p>A ØMQ socket can select a security mechanism. Both peers must use the same security mechanism.</p>
 *
 * <p>The following security mechanisms are provided for IPC and TCP connections:</p>
 * <ul>
 * <li>Null security</li>
 * <li>Plain-text authentication using username and password</li>
 * <li>Elliptic curve authentication and encryption</li>
 * </ul>
 */
public class ZMQ
{
    /**
     * Socket flag to indicate that more message parts are coming.
     */
    public static final int SNDMORE = zmq.ZMQ.ZMQ_SNDMORE;

    // Values for flags in Socket's send and recv functions.
    /**
     * Socket flag to indicate a nonblocking send or recv mode.
     */
    public static final int DONTWAIT = zmq.ZMQ.ZMQ_DONTWAIT;
    public static final int NOBLOCK  = zmq.ZMQ.ZMQ_DONTWAIT;

    // Socket types, used when creating a Socket. Note that all the int types here is
    // deprecated, use SocketType instead

    /**
     * @deprecated use {@link SocketType#PAIR} instead
     */
    @Deprecated
    public static final int PAIR = zmq.ZMQ.ZMQ_PAIR;

    /**
     * @deprecated use {@link SocketType#PUB} instead
     */
    @Deprecated
    public static final int PUB = zmq.ZMQ.ZMQ_PUB;
    /**
     * @deprecated use {@link SocketType#SUB} instead
     */
    @Deprecated
    public static final int SUB = zmq.ZMQ.ZMQ_SUB;

    /**
     * @deprecated use {@link SocketType#REQ} instead
     */
    @Deprecated
    public static final int REQ = zmq.ZMQ.ZMQ_REQ;

    /**
     * @deprecated use {@link SocketType#REP} instead
     */
    @Deprecated
    public static final int REP = zmq.ZMQ.ZMQ_REP;

    /**
     * @deprecated use {@link SocketType#DISH} instead
     */
    @Deprecated
    public static final int DISH = zmq.ZMQ.ZMQ_DISH;

    /**
     * @deprecated use {@link SocketType#RADIO} instead
     */
    @Deprecated
    public static final int RADIO = zmq.ZMQ.ZMQ_RADIO;

    /**
     * @deprecated use {@link SocketType#DEALER} instead
     */
    @Deprecated
    public static final int DEALER = zmq.ZMQ.ZMQ_DEALER;
    /**
     * Old alias for DEALER flag.
     * Flag to specify a XREQ socket, receiving side must be a XREP.
     *
     * @deprecated As of release 3.0 of zeromq, replaced by {@link #DEALER}
     */
    @Deprecated
    public static final int XREQ   = DEALER;

    @Deprecated
    public static final int ROUTER = zmq.ZMQ.ZMQ_ROUTER;
    /**
     * Old alias for ROUTER flag.
     * Flag to specify the receiving part of a XREQ socket.
     *
     * @deprecated As of release 3.0 of zeromq, replaced by {@link #ROUTER}
     */
    @Deprecated
    public static final int XREP   = ROUTER;

    @Deprecated
    public static final int PULL = zmq.ZMQ.ZMQ_PULL;

    @Deprecated
    public static final int PUSH = zmq.ZMQ.ZMQ_PUSH;

    @Deprecated
    public static final int XPUB = zmq.ZMQ.ZMQ_XPUB;

    @Deprecated
    public static final int XSUB = zmq.ZMQ.ZMQ_XSUB;

    @Deprecated
    public static final int STREAM     = zmq.ZMQ.ZMQ_STREAM;
    /**
     * Flag to specify a STREAMER device.
     */
    @Deprecated
    public static final int STREAMER   = zmq.ZMQ.ZMQ_STREAMER;
    /**
     * Flag to specify a FORWARDER device.
     */
    @Deprecated
    public static final int FORWARDER  = zmq.ZMQ.ZMQ_FORWARDER;
    /**
     * Flag to specify a QUEUE device.
     */
    @Deprecated
    public static final int QUEUE      = zmq.ZMQ.ZMQ_QUEUE;
    /**
     * @see org.zeromq.ZMQ#PULL
     */
    @Deprecated
    public static final int UPSTREAM   = PULL;
    /**
     * @see org.zeromq.ZMQ#PUSH
     */
    @Deprecated
    public static final int DOWNSTREAM = PUSH;

    /**
     * EVENT_CONNECTED: connection established.
     * The EVENT_CONNECTED event triggers when a connection has been
     * established to a remote peer. This can happen either synchronous
     * or asynchronous. Value is the FD of the newly connected socket.
     */
    public static final int EVENT_CONNECTED          = zmq.ZMQ.ZMQ_EVENT_CONNECTED;
    /**
     * EVENT_CONNECT_DELAYED: synchronous connect failed, it's being polled.
     * The EVENT_CONNECT_DELAYED event triggers when an immediate connection
     * attempt is delayed and its completion is being polled for. Value has
     * no meaning.
     */
    public static final int EVENT_CONNECT_DELAYED    = zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED;
    /**
     * @see org.zeromq.ZMQ#EVENT_CONNECT_DELAYED
     */
    @Deprecated
    public static final int EVENT_DELAYED            = EVENT_CONNECT_DELAYED;
    /**
     * EVENT_CONNECT_RETRIED: asynchronous connect / reconnection attempt.
     * The EVENT_CONNECT_RETRIED event triggers when a connection attempt is
     * being handled by reconnect timer. The reconnect interval's recomputed
     * for each attempt. Value is the reconnect interval.
     */
    public static final int EVENT_CONNECT_RETRIED    = zmq.ZMQ.ZMQ_EVENT_CONNECT_RETRIED;
    /**
     * @see org.zeromq.ZMQ#EVENT_CONNECT_RETRIED
     */
    @Deprecated
    public static final int EVENT_RETRIED            = EVENT_CONNECT_RETRIED;
    /**
     * EVENT_LISTENING: socket bound to an address, ready to accept connections.
     * The EVENT_LISTENING event triggers when a socket's successfully bound to
     * a an interface. Value is the FD of the newly bound socket.
     */
    public static final int EVENT_LISTENING          = zmq.ZMQ.ZMQ_EVENT_LISTENING;
    /**
     * EVENT_BIND_FAILED: socket could not bind to an address.
     * The EVENT_BIND_FAILED event triggers when a socket could not bind to a
     * given interface. Value is the errno generated by the bind call.
     */
    public static final int EVENT_BIND_FAILED        = zmq.ZMQ.ZMQ_EVENT_BIND_FAILED;
    /**
     * EVENT_ACCEPTED: connection accepted to bound interface.
     * The EVENT_ACCEPTED event triggers when a connection from a remote peer
     * has been established with a socket's listen address. Value is the FD of
     * the accepted socket.
     */
    public static final int EVENT_ACCEPTED           = zmq.ZMQ.ZMQ_EVENT_ACCEPTED;
    /**
     * EVENT_ACCEPT_FAILED: could not accept client connection.
     * The EVENT_ACCEPT_FAILED event triggers when a connection attempt to a
     * socket's bound address fails. Value is the errno generated by accept.
     */
    public static final int EVENT_ACCEPT_FAILED      = zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED;
    /**
     * EVENT_CLOSED: connection closed.
     * The EVENT_CLOSED event triggers when a connection's underlying
     * descriptor has been closed. Value is the former FD of the for the
     * closed socket. FD has been closed already!
     */
    public static final int EVENT_CLOSED             = zmq.ZMQ.ZMQ_EVENT_CLOSED;
    /**
     * EVENT_CLOSE_FAILED: connection couldn't be closed.
     * The EVENT_CLOSE_FAILED event triggers when a descriptor could not be
     * released back to the OS. Implementation note: ONLY FOR IPC SOCKETS.
     * Value is the errno generated by unlink.
     */
    public static final int EVENT_CLOSE_FAILED       = zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED;
    /**
     * EVENT_DISCONNECTED: broken session.
     * The EVENT_DISCONNECTED event triggers when the stream engine (tcp and
     * ipc specific) detects a corrupted / broken session. Value is the FD of
     * the socket.
     */
    public static final int EVENT_DISCONNECTED       = zmq.ZMQ.ZMQ_EVENT_DISCONNECTED;
    /**
     * EVENT_MONITOR_STOPPED: monitor has been stopped.
     * The EVENT_MONITOR_STOPPED event triggers when the monitor for a socket is
     * stopped.
     */
    public static final int EVENT_MONITOR_STOPPED    = zmq.ZMQ.ZMQ_EVENT_MONITOR_STOPPED;
    /**
     * EVENT_HANDSHAKE_PROTOCOL: protocol has been successfully negotiated.
     * The EVENT_HANDSHAKE_PROTOCOL event triggers when the stream engine (tcp and ipc)
     * successfully negotiated a protocol version with the peer. Value is the version number
     * (0 for unversioned, 3 for V3).
     */
    public static final int EVENT_HANDSHAKE_PROTOCOL = zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL;
    /**
     * EVENT_ALL: all events known.
     * The EVENT_ALL constant can be used to set up a monitor for all known events.
     */
    public static final int EVENT_ALL                = zmq.ZMQ.ZMQ_EVENT_ALL;

    /**
     * Unspecified system errors during handshake. Event value is an errno.
     */
    public static final int HANDSHAKE_FAILED_NO_DETAIL   = zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_NO_DETAIL;

    /**
     * Handshake complete successfully with successful authentication (if
     *  enabled). Event value is unused.
     */
    public static final int HANDSHAKE_SUCCEEDED      = zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_SUCCEEDED;

    /**
     *   Protocol errors between ZMTP peers or between server and ZAP handler.
     *  Event value is one of ZMQ_PROTOCOL_ERROR_*
     */
    public static final int HANDSHAKE_FAILED_PROTOCOL = zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_PROTOCOL;
    /**
     *   Failed authentication requests. Event value is the numeric ZAP status
     *  code, i.e. 300, 400 or 500.
     */
    public static final int HANDSHAKE_FAILED_AUTH     = zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_AUTH;

    public static final byte[] MESSAGE_SEPARATOR = zmq.ZMQ.MESSAGE_SEPARATOR;

    public static final byte[] SUBSCRIPTION_ALL = zmq.ZMQ.SUBSCRIPTION_ALL;

    public static final byte[] PROXY_PAUSE     = zmq.ZMQ.PROXY_PAUSE;
    public static final byte[] PROXY_RESUME    = zmq.ZMQ.PROXY_RESUME;
    public static final byte[] PROXY_TERMINATE = zmq.ZMQ.PROXY_TERMINATE;

    public static final Charset CHARSET = zmq.ZMQ.CHARSET;

    private ZMQ()
    {
    }

    /**
     * Create a new Context.
     *
     * @param ioThreads Number of threads to use, usually 1 is sufficient for most use cases.
     * @return the Context
     */
    public static Context context(int ioThreads)
    {
        return new Context(ioThreads);
    }

    @Deprecated
    public static boolean device(int type, Socket frontend, Socket backend)
    {
        return zmq.ZMQ.proxy(frontend.base, backend.base, null);
    }

    /**
     * Starts the built-in 0MQ proxy in the current application thread.
     * The proxy connects a frontend socket to a backend socket. Conceptually, data flows from frontend to backend.
     * Depending on the socket types, replies may flow in the opposite direction. The direction is conceptual only;
     * the proxy is fully symmetric and there is no technical difference between frontend and backend.
     * <p>
     * Before calling ZMQ.proxy() you must set any socket options, and connect or bind both frontend and backend sockets.
     * The two conventional proxy models are:
     * <p>
     * ZMQ.proxy() runs in the current thread and returns only if/when the current context is closed.
     *
     * @param frontend ZMQ.Socket
     * @param backend  ZMQ.Socket
     * @param capture  If the capture socket is not NULL, the proxy shall send all messages, received on both
     *                 frontend and backend, to the capture socket. The capture socket should be a
     *                 ZMQ_PUB, ZMQ_DEALER, ZMQ_PUSH, or ZMQ_PAIR socket.
     */
    public static boolean proxy(Socket frontend, Socket backend, Socket capture)
    {
        return zmq.ZMQ.proxy(frontend.base, backend.base, capture != null ? capture.base : null);
    }

    public static boolean proxy(Socket frontend, Socket backend, Socket capture, Socket control)
    {
        return zmq.ZMQ.proxy(
                             frontend.base,
                             backend.base,
                             capture == null ? null : capture.base,
                             control == null ? null : control.base);
    }

    public static int poll(Selector selector, PollItem[] items, long timeout)
    {
        return poll(selector, items, items.length, timeout);
    }

    public static int poll(Selector selector, PollItem[] items, int count, long timeout)
    {
        zmq.poll.PollItem[] pollItems = new zmq.poll.PollItem[count];
        for (int i = 0; i < count; i++) {
            pollItems[i] = items[i].base;
        }

        return zmq.ZMQ.poll(selector, pollItems, count, timeout);
    }

    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getMajorVersion()
    {
        return zmq.ZMQ.ZMQ_VERSION_MAJOR;
    }

    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getMinorVersion()
    {
        return zmq.ZMQ.ZMQ_VERSION_MINOR;
    }

    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getPatchVersion()
    {
        return zmq.ZMQ.ZMQ_VERSION_PATCH;
    }

    /**
     * @return Full version number of the ZMQ library used for comparing versions.
     */
    public static int getFullVersion()
    {
        return zmq.ZMQ.makeVersion(zmq.ZMQ.ZMQ_VERSION_MAJOR, zmq.ZMQ.ZMQ_VERSION_MINOR, zmq.ZMQ.ZMQ_VERSION_PATCH);
    }

    /**
     * @param major Version major component.
     * @param minor Version minor component.
     * @param patch Version patch component.
     * @return Comparible single int version number.
     */
    public static int makeVersion(final int major, final int minor, final int patch)
    {
        return zmq.ZMQ.makeVersion(major, minor, patch);
    }

    /**
     * @return String version number in the form major.minor.patch.
     */
    public static String getVersionString()
    {
        return "" + zmq.ZMQ.ZMQ_VERSION_MAJOR + "." + zmq.ZMQ.ZMQ_VERSION_MINOR + "." + zmq.ZMQ.ZMQ_VERSION_PATCH;
    }

    public static void msleep(long millis)
    {
        zmq.ZMQ.msleep(millis);
    }

    public static void sleep(long seconds)
    {
        zmq.ZMQ.sleep(seconds);
    }

    public static void sleep(long amount, TimeUnit unit)
    {
        zmq.ZMQ.sleep(amount, unit);
    }

    /**
     * Resolve code from errornumber.
     * <p>
     * Messages are taken from https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/errno.h.html
     */
    public enum Error
    {
        NOERROR(0, "No error"),
        ENOTSUP(ZError.ENOTSUP, "Not supported"),
        EPROTONOSUPPORT(ZError.EPROTONOSUPPORT, "Protocol not supported"),
        ENOBUFS(ZError.ENOBUFS, "No buffer space available"),
        ENETDOWN(ZError.ENETDOWN, "Network is down"),
        EADDRINUSE(ZError.EADDRINUSE, "Address already in use"),
        EADDRNOTAVAIL(ZError.EADDRNOTAVAIL, "Address not available"),
        ECONNREFUSED(ZError.ECONNREFUSED, "Connection refused"),
        EINPROGRESS(ZError.EINPROGRESS, "Operation in progress"),
        EHOSTUNREACH(ZError.EHOSTUNREACH, "Host unreachable"),
        EMTHREAD(ZError.EMTHREAD, "No thread available"),
        EFSM(ZError.EFSM, "Operation cannot be accomplished in current state"),
        ENOCOMPATPROTO(ZError.ENOCOMPATPROTO, "The protocol is not compatible with the socket type"),
        ETERM(ZError.ETERM, "Context was terminated"),
        ENOTSOCK(ZError.ENOTSOCK, "Not a socket"),
        EAGAIN(ZError.EAGAIN, "Resource unavailable, try again"),
        ENOENT(ZError.ENOENT, "No such file or directory"),
        EINTR(ZError.EINTR, "Interrupted function"),
        EACCESS(ZError.EACCESS, "Permission denied"),
        EFAULT(ZError.EFAULT, "Bad address"),
        EINVAL(ZError.EINVAL, "Invalid argument"),
        EISCONN(ZError.EISCONN, "Socket is connected"),
        ENOTCONN(ZError.ENOTCONN, "The socket is not connected"),
        EMSGSIZE(ZError.EMSGSIZE, "Message too large"),
        EAFNOSUPPORT(ZError.EAFNOSUPPORT, "Address family not supported"),
        ENETUNREACH(ZError.ENETUNREACH, "Network unreachable"),
        ECONNABORTED(ZError.ECONNABORTED, "Connection aborted"),
        ECONNRESET(ZError.ECONNRESET, "Connection reset"),
        ETIMEDOUT(ZError.ETIMEDOUT, "Connection timed out"),
        ENETRESET(ZError.ENETRESET, "Connection aborted by network"),
        EIOEXC(ZError.EIOEXC),
        ESOCKET(ZError.ESOCKET),
        EMFILE(ZError.EMFILE, "File descriptor value too large"),
        EPROTO(ZError.EPROTO, "Protocol error");

        private static final Map<Integer, Error> map = new HashMap<>(Error.values().length);
        static {
            for (Error e : Error.values()) {
                map.put(e.code, e);
            }
        }
        private final int code;
        private final String message;

        Error(int code)
        {
            this.code = code;
            this.message = "errno " + code;
        }

        Error(int code, String message)
        {
            this.code = code;
            this.message = message;
        }

        public static Error findByCode(int code)
        {
            if (code <= 0) {
                return NOERROR;
            }
            else if (map.containsKey(code)) {
                return map.get(code);
            }
            else {
                throw new IllegalArgumentException("Unknown " + Error.class.getName() + " enum code: " + code);
            }
        }

        public int getCode()
        {
            return code;
        }

        public String getMessage()
        {
            return message;
        }
    }

    /**
     * Container for all sockets in a single process,
     * acting as the transport for inproc sockets,
     * which are the fastest way to connect threads in one process.
     */
    public static class Context implements Closeable
    {
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Ctx           ctx;

        /**
         * Class constructor.
         *
         * @param ioThreads size of the threads pool to handle I/O operations.
         */
        protected Context(int ioThreads)
        {
            ctx = zmq.ZMQ.init(ioThreads);
        }

        /**
         * Returns true if terminate() has been called on ctx.
         */
        public boolean isTerminated()
        {
            return !ctx.isActive();
        }

        /**
         * The size of the 0MQ thread pool to handle I/O operations.
         */
        public int getIOThreads()
        {
            return ctx.get(zmq.ZMQ.ZMQ_IO_THREADS);
        }

        /**
         * Set the size of the 0MQ thread pool to handle I/O operations.
         * @throws IllegalStateException If context was already initialized by the creation of a socket
         */
        public boolean setIOThreads(int ioThreads)
        {
            return ctx.set(zmq.ZMQ.ZMQ_IO_THREADS, ioThreads);
        }

        /**
         * The maximum number of sockets allowed on the context
         */
        public int getMaxSockets()
        {
            return ctx.get(zmq.ZMQ.ZMQ_MAX_SOCKETS);
        }

        /**
         * Sets the maximum number of sockets allowed on the context
         * @throws IllegalStateException If context was already initialized by the creation of a socket
         */
        public boolean setMaxSockets(int maxSockets)
        {
            return ctx.set(zmq.ZMQ.ZMQ_MAX_SOCKETS, maxSockets);
        }

        /**
         * @deprecated use {@link #isBlocky()} instead
         */
        @Deprecated
        public boolean getBlocky()
        {
            return isBlocky();
        }

        public boolean isBlocky()
        {
            return ctx.get(zmq.ZMQ.ZMQ_BLOCKY) != 0;
        }

        public boolean setBlocky(boolean block)
        {
            return ctx.set(zmq.ZMQ.ZMQ_BLOCKY, block ? 1 : 0);
        }

        public boolean isIPv6()
        {
            return ctx.get(zmq.ZMQ.ZMQ_IPV6) != 0;
        }

        public boolean getIPv6()
        {
            return isIPv6();
        }

        public boolean setIPv6(boolean ipv6)
        {
            return ctx.set(zmq.ZMQ.ZMQ_IPV6, ipv6 ? 1 : 0);
        }

        /**
         * Set the handler invoked when a {@link zmq.poll.Poller} abruptly terminates due to an uncaught exception.<p>
         * It default to the value of {@link Thread#getDefaultUncaughtExceptionHandler()}
         * @param handler The object to use as this thread's uncaught exception handler. If null then this thread has no explicit handler.
         * @throws IllegalStateException If context was already initialized by the creation of a socket
         */
        public void setUncaughtExceptionHandler(UncaughtExceptionHandler handler)
        {
            ctx.setUncaughtExceptionHandler(handler);
        }

        /**
         * @return The handler invoked when a {@link zmq.poll.Poller} abruptly terminates due to an uncaught exception.
         */
        public UncaughtExceptionHandler getUncaughtExceptionHandler()
        {
            return ctx.getUncaughtExceptionHandler();
        }

        /**
         * In {@link zmq.poll.Poller#run()}, some non-fatal exceptions can be thrown. This handler will be notified, so they can
         * be logged.<p>
         * Default to {@link Throwable#printStackTrace()}
         * @param handler The object to use as this thread's handler for recoverable exceptions notifications.
         * @throws IllegalStateException If context was already initialized by the creation of a socket
         */
        public void setNotificationExceptionHandler(UncaughtExceptionHandler handler)
        {
            ctx.setNotificationExceptionHandler(handler);
        }

        /**
         * @return The handler invoked when a non-fatal exceptions is thrown in zmq.poll.Poller#run()
         */
        public UncaughtExceptionHandler getNotificationExceptionHandler()
        {
            return ctx.getNotificationExceptionHandler();
        }

        /**
         * Used to define a custom thread factory. It can be used to create thread that will be bounded to a CPU for
         * performance or tweaks the created thread. It the UncaughtExceptionHandler is not set, the created thread UncaughtExceptionHandler
         * will not be changed, so the factory can also be used to set it.
         *
         * @param threadFactory the thread factory used by {@link zmq.poll.Poller}
         * @throws IllegalStateException If context was already initialized by the creation of a socket
         */
        public void setThreadFactor(BiFunction<Runnable, String, Thread> threadFactory)
        {
            ctx.setThreadFactory(threadFactory);
        }

        /**
         * @return the current thread factory
         */
        public BiFunction<Runnable, String, Thread> getThreadFactory()
        {
            return ctx.getThreadFactory();
        }

        /**
         * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ
         * Context has been disposed of.
         */
        public void term()
        {
            if (closed.compareAndSet(false, true)) {
                ctx.terminate();
            }
        }

        public boolean isClosed()
        {
            return closed.get();
        }

        /**
         * Creates a ØMQ socket within the specified context and return an opaque handle to the newly created socket.
         * <br>
         * The type argument specifies the socket type, which determines the semantics of communication over the socket.
         * <br>
         * The newly created socket is initially unbound, and not associated with any endpoints.
         * <br>
         * In order to establish a message flow a socket must first be connected
         * to at least one endpoint with {@link org.zeromq.ZMQ.Socket#connect(String)},
         * or at least one endpoint must be created for accepting incoming connections with {@link org.zeromq.ZMQ.Socket#bind(String)}.
         *
         * @param type the socket type.
         * @return the newly created Socket.
         */
        public Socket socket(SocketType type)
        {
            return new Socket(this, type);
        }

        @Deprecated
        public Socket socket(int type)
        {
            return socket(SocketType.type(type));
        }

        /**
         * Create a new Selector within this context.
         *
         * @return the newly created Selector.
         */
        public Selector selector()
        {
            return ctx.createSelector();
        }

        /**
         * Closes a Selector that was created within this context.
         *
         * @param selector the Selector to close.
         * @return true if the selector was closed. otherwise false
         * (mostly because it was not created by the context).
         */
        public boolean close(Selector selector)
        {
            return ctx.closeSelector(selector);
        }

        /**
         * Create a new Poller within this context, with a default size.
         * DO NOT FORGET TO CLOSE THE POLLER AFTER USE with {@link Poller#close()}
         *
         * @return the newly created Poller.
         */
        public Poller poller()
        {
            return new Poller(this);
        }

        /**
         * Create a new Poller within this context, with a specified initial size.
         * DO NOT FORGET TO CLOSE THE POLLER AFTER USE with {@link Poller#close()}
         *
         * @param size the poller initial size.
         * @return the newly created Poller.
         */
        public Poller poller(int size)
        {
            return new Poller(this, size);
        }

        /**
         * Destroys the ØMQ context context.
         * Context termination is performed in the following steps:
         * <ul>
         * <li>Any blocking operations currently in progress on sockets open within context
         * shall return immediately with an error code of ETERM.
         * With the exception of {@link ZMQ.Socket#close()}, any further operations on sockets
         * open within context shall fail with an error code of ETERM.</li>
         * <li>After interrupting all blocking calls, this method shall block until the following conditions are satisfied:
         * <ul>
         * <li>All sockets open within context have been closed with {@link ZMQ.Socket#close()}.</li>
         * <li>For each socket within context, all messages sent by the application with {@link ZMQ.Socket#send} have either
         * been physically transferred to a network peer,
         * or the socket's linger period set with the {@link ZMQ.Socket#setLinger(int)} socket option has expired.</li>
         * </ul>
         * </li>
         * </ul>
         * <strong>Warning</strong>
         * <br>
         * As ZMQ_LINGER defaults to "infinite", by default this method will block indefinitely if there are any pending connects or sends.
         * We strongly recommend to
         * <ul>
         * <li>set ZMQ_LINGER to zero on all sockets </li>
         * <li>close all sockets, before calling this method</li>
         * </ul>
         */
        @Override
        public void close()
        {
            term();
        }
    }

    /**
     * Abstracts an asynchronous message queue, with the exact queuing semantics depending on the socket type in use.
     * <br>
     * Where conventional sockets transfer streams of bytes or discrete datagrams, ØMQ sockets transfer discrete messages.
     * <h2>Key differences to conventional sockets</h2>
     * Generally speaking, conventional sockets present a synchronous interface to either
     * connection-oriented reliable byte streams (SOCK_STREAM),
     * or connection-less unreliable datagrams (SOCK_DGRAM).
     * <br>
     * In comparison, ØMQ sockets present an abstraction of an asynchronous message queue,
     * with the exact queueing semantics depending on the socket type in use.
     * Where conventional sockets transfer streams of bytes or discrete datagrams, ØMQ sockets transfer discrete messages.
     * <br>
     * ØMQ sockets being asynchronous means that the timings of the physical connection setup and tear down, reconnect and effective delivery
     * are transparent to the user and organized by ØMQ itself.
     * Further, messages may be queued in the event that a peer is unavailable to receive them.
     * <br>
     * Conventional sockets allow only strict one-to-one (two peers), many-to-one (many clients, one server), or in some cases one-to-many (multicast) relationships.
     * With the exception of {@link ZMQ#PAIR}, ØMQ sockets may be connected to multiple endpoints using {@link ZMQ.Socket#connect(String)},
     * while simultaneously accepting incoming connections from multiple endpoints bound to the socket using {@link ZMQ.Socket#bind(String)},
     * thus allowing many-to-many relationships.
     * <h2>Thread safety</h2>
     * ØMQ sockets are not thread safe. <strong>Applications MUST NOT use a socket from multiple threads</strong>
     * except after migrating a socket from one thread to another with a "full fence" memory barrier.
     * <br>
     * ØMQ sockets are not Thread.interrupt safe. <strong>Applications MUST NOT interrupt threads using ØMQ sockets</strong>.
     * <h2>Messaging patterns</h2>
     * <ul>
     * <li>Request-reply
     * <br>
     * The request-reply pattern is used for sending requests from a {@link ZMQ#REQ} client to one or more {@link ZMQ#REP} services, and receiving subsequent replies to each request sent.
     * The request-reply pattern is formally defined by http://rfc.zeromq.org/spec:28.
     * {@link ZMQ#REQ}, {@link ZMQ#REP}, {@link ZMQ#DEALER}, {@link ZMQ#ROUTER} socket types belong to this pattern.
     * </li>
     * <li>Publish-subscribe
     * <br>
     * The publish-subscribe pattern is used for one-to-many distribution of data from a single publisher to multiple subscribers in a fan out fashion.
     * The publish-subscribe pattern is formally defined by http://rfc.zeromq.org/spec:29.
     * {@link ZMQ#SUB}, {@link ZMQ#PUB}, {@link ZMQ#XSUB}, {@link ZMQ#XPUB} socket types belong to this pattern.
     * </li>
     * <li>Pipeline
     * <br>
     * The pipeline pattern is used for distributing data to nodes arranged in a pipeline. Data always flows down the pipeline, and each stage of the pipeline is connected to at least one node.
     * When a pipeline stage is connected to multiple nodes data is round-robined among all connected nodes.
     * The pipeline pattern is formally defined by http://rfc.zeromq.org/spec:30.
     * {@link ZMQ#PUSH}, {@link ZMQ#PULL} socket types belong to this pattern.
     * </li>
     * <li>Exclusive pair
     * <br>
     * The exclusive pair pattern is used to connect a peer to precisely one other peer. This pattern is used for inter-thread communication across the inproc transport,
     * using {@link ZMQ#PAIR} socket type.
     * The exclusive pair pattern is formally defined by http://rfc.zeromq.org/spec:31.
     * </li>
     * <li>Native
     * <br>
     * The native pattern is used for communicating with TCP peers and allows asynchronous requests and replies in either direction,
     * using {@link ZMQ#STREAM} socket type.
     * </li>
     * </ul>
     */
    public static class Socket implements Closeable
    {
        //  This port range is defined by IANA for dynamic or private ports
        //  We use this when choosing a port for dynamic binding.
        private static final int DYNFROM = 0xc000;
        private static final int DYNTO   = 0xffff;

        private final Consumer<Socket> socketClose;
        private final SocketBase    base;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);

        /**
         * Class constructor.
         *
         * @param context a 0MQ context previously created.
         * @param type    the socket type.
         */
        protected Socket(Context context, SocketType type)
        {
            this(context.ctx, type.type, null);
        }

        /**
         * Class constructor.
         *
         * @param context a 0MQ context previously created.
         * @param type    the socket type.
         */
        protected Socket(ZContext context, SocketType type)
        {
            this(context.getContext().ctx, type.type, context::closeSocket);
        }

        /**
         * Class constructor.
         *
         * @param context a 0MQ context previously created.
         * @param type    the socket type.
         * @deprecated use {@link Socket#Socket(Context, SocketType)}
         */
        @Deprecated
        protected Socket(Context context, int type)
        {
            this(context.ctx, type, null);
        }

        /**
         * Wrap an already existing socket
         * @param base an already generated socket
         */
        protected Socket(SocketBase base)
        {
            this.socketClose = s -> internalClose();
            this.base = base;
        }

        private Socket(Ctx ctx, int type, Consumer<Socket> socketClose)
        {
            this.base = ctx.createSocket(type);
            this.socketClose = socketClose != null ? socketClose : s -> internalClose();
        }

        /**
         * DO NOT USE if you're trying to build a special proxy
         *
         * @return raw zmq.SocketBase
         */
        public SocketBase base()
        {
            return base;
        }

        /**
         * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ Socket
         * has been disposed of. If the socket was created from a org.zeromq.ZContext, it will remove
         * the reference to this socket from it.
         */
        @Override
        public void close()
        {
            socketClose.accept(this);
        }

        void internalClose()
        {
            if (isClosed.compareAndSet(false, true)) {
                base.close();
            }
        }

        /**
         * The 'ZMQ_TYPE option shall retrieve the socket type for the specified
         * 'socket'.  The socket type is specified at socket creation time and
         * cannot be modified afterwards.
         *
         * @return the socket type.
         */
        public int getType()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TYPE);
        }

        /**
         * The 'ZMQ_TYPE option shall retrieve the socket type for the specified
         * 'socket'.  The socket type is specified at socket creation time and
         * cannot be modified afterwards.
         *
         * @return the socket type as an enum.
         */
        public SocketType getSocketType()
        {
            return SocketType.type(getType());
        }

        /**
         *
         * @return the low level {@link Ctx} associated with this socket.
         */
        public Ctx getCtx()
        {
            return base.getCtx();
        }

        /**
         * The 'ZMQ_LINGER' option shall retrieve the period for pending outbound
         * messages to linger in memory after closing the socket. Value of -1 means
         * infinite. Pending messages will be kept until they are fully transferred to
         * the peer. Value of 0 means that all the pending messages are dropped immediately
         * when socket is closed. Positive value means number of milliseconds to keep
         * trying to send the pending messages before discarding them.
         *
         * @return the linger period.
         * @see #setLinger(int)
         */
        public int getLinger()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_LINGER);
        }

        private boolean setSocketOpt(int option, Object value)
        {
            try {
                boolean set = base.setSocketOpt(option, value);
                set &= base.errno() != ZError.EINVAL;
                return set;
            }
            catch (CtxTerminatedException e) {
                return false;
            }
        }

        /**
         * The ZMQ_LINGER option shall set the linger period for the specified socket.
         * The linger period determines how long pending messages which have yet to be sent to a peer
         * shall linger in memory after a socket is disconnected with disconnect or closed with close,
         * and further affects the termination of the socket's context with Ctx#term.
         * The following outlines the different behaviours:
         * <p>A value of -1 specifies an infinite linger period.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until all pending messages have been sent to a peer.
         * <p>
         * The value of 0 specifies no linger period. Pending messages shall be discarded immediately after a call to disconnect() or close().
         * <p>
         * Positive values specify an upper bound for the linger period in milliseconds.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until either all pending messages have been sent to a peer,
         * or the linger period expires, after which any pending messages shall be discarded.
         *
         * @param value the linger period in milliseconds.
         * @return true if the option was set, otherwise false
         * @see #getLinger()
         * @deprecated the linger option has only integer range, use {@link #setLinger(int)} instead
         */
        @Deprecated
        public boolean setLinger(long value)
        {
            return setLinger(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_LINGER option shall set the linger period for the specified socket.
         * The linger period determines how long pending messages which have yet to be sent to a peer
         * shall linger in memory after a socket is disconnected with disconnect or closed with close,
         * and further affects the termination of the socket's context with Ctx#term.
         * The following outlines the different behaviours:
         * <p>A value of -1 specifies an infinite linger period.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until all pending messages have been sent to a peer.
         * <p>
         * The value of 0 specifies no linger period. Pending messages shall be discarded immediately after a call to disconnect() or close().
         * <p>
         * Positive values specify an upper bound for the linger period in milliseconds.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until either all pending messages have been sent to a peer,
         * or the linger period expires, after which any pending messages shall be discarded.
         *
         * @param value the linger period in milliseconds.
         * @return true if the option was set, otherwise false
         * @see #getLinger()
         */
        public boolean setLinger(int value)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_LINGER, value);
        }

        /**
         * The ZMQ_RECONNECT_IVL option shall retrieve the initial reconnection interval for the specified socket.
         * The reconnection interval is the period ØMQ shall wait between attempts to reconnect
         * disconnected peers when using connection-oriented transports.
         * The value -1 means no reconnection.
         * <p>
         * CAUTION: The reconnection interval may be randomized by ØMQ to prevent reconnection storms in topologies with a large number of peers per socket.
         *
         * @return the reconnectIVL.
         * @see #setReconnectIVL(int)
         */
        public int getReconnectIVL()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RECONNECT_IVL);
        }

        /**
         * The ZMQ_RECONNECT_IVL option shall set the initial reconnection interval for the specified socket.
         * The reconnection interval is the period ØMQ shall wait between attempts
         * to reconnect disconnected peers when using connection-oriented transports.
         * The value -1 means no reconnection.
         *
         * @return true if the option was set, otherwise false
         * @see #getReconnectIVL()
         * @deprecated reconnect interval option uses integer range, use {@link #setReconnectIVL(int)} instead
         */
        @Deprecated
        public boolean setReconnectIVL(long value)
        {
            return setReconnectIVL(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_RECONNECT_IVL option shall set the initial reconnection interval for the specified socket.
         * The reconnection interval is the period ØMQ shall wait between attempts
         * to reconnect disconnected peers when using connection-oriented transports.
         * The value -1 means no reconnection.
         *
         * @return true if the option was set, otherwise false.
         * @see #getReconnectIVL()
         */
        public boolean setReconnectIVL(int value)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_RECONNECT_IVL, value);
        }

        /**
         * The ZMQ_BACKLOG option shall retrieve the maximum length of the queue
         * of outstanding peer connections for the specified socket;
         * this only applies to connection-oriented transports.
         * For details refer to your operating system documentation for the listen function.
         *
         * @return the the maximum length of the queue of outstanding peer connections.
         * @see #setBacklog(int)
         */
        public int getBacklog()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_BACKLOG);
        }

        /**
         * The ZMQ_BACKLOG option shall set the maximum length
         * of the queue of outstanding peer connections for the specified socket;
         * this only applies to connection-oriented transports.
         * For details refer to your operating system documentation for the listen function.
         *
         * @param value the maximum length of the queue of outstanding peer connections.
         * @return true if the option was set, otherwise false.
         * @see #getBacklog()
         * @deprecated this option uses integer range, use {@link #setBacklog(int)} instead.
         */
        @Deprecated
        public boolean setBacklog(long value)
        {
            return setBacklog(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_BACKLOG option shall set the maximum length
         * of the queue of outstanding peer connections for the specified socket;
         * this only applies to connection-oriented transports.
         * For details refer to your operating system documentation for the listen function.
         *
         * @param value the maximum length of the queue of outstanding peer connections.
         * @return true if the option was set, otherwise false.
         * @see #getBacklog()
         */
        public boolean setBacklog(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_BACKLOG, value);
        }

        /**
         * The ZMQ_HANDSHAKE_IVL option shall retrieve the maximum handshake interval
         * for the specified socket.
         * Handshaking is the exchange of socket configuration information
         * (socket type, identity, security) that occurs when a connection is first opened,
         * only for connection-oriented transports.
         * If handshaking does not complete within the configured time,
         * the connection shall be closed. The value 0 means no handshake time limit.
         *
         * @return the maximum handshake interval.
         * @see #setHandshakeIvl(int)
         */
        public int getHandshakeIvl()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_HANDSHAKE_IVL);
        }

        /**
         * The ZMQ_HEARTBEAT_IVL option shall set the interval
         * between sending ZMTP heartbeats for the specified socket.
         * If this option is set and is greater than 0,
         * then a PING ZMTP command will be sent every ZMQ_HEARTBEAT_IVL milliseconds.
         *
         * @return heartbeat interval in milliseconds
         */
        public int getHeartbeatIvl()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_IVL);
        }

        /**
         * The ZMQ_HEARTBEAT_TIMEOUT option shall set
         * how long to wait before timing-out a connection
         * after sending a PING ZMTP command and not receiving any traffic.
         * This option is only valid if ZMQ_HEARTBEAT_IVL is also set,
         * and is greater than 0. The connection will time out
         * if there is no traffic received after sending the PING command,
         * but the received traffic does not have to be a PONG command
         * - any received traffic will cancel the timeout.
         *
         * @return heartbeat timeout in milliseconds
         */
        public int getHeartbeatTimeout()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_TIMEOUT);
        }

        /**
         * The ZMQ_HEARTBEAT_TTL option shall set the timeout
         * on the remote peer for ZMTP heartbeats.
         * If this option is greater than 0,
         * the remote side shall time out the connection
         * if it does not receive any more traffic within the TTL period.
         * This option does not have any effect if ZMQ_HEARTBEAT_IVL is not set or is 0.
         * Internally, this value is rounded down to the nearest decisecond,
         * any value less than 100 will have no effect.
         *
         * @return heartbeat time-to-live in milliseconds
         */
        public int getHeartbeatTtl()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_TTL);
        }

        /**
         * The ZMQ_HEARTBEAT_CONTEXT option shall set the ping context
         * of the peer for ZMTP heartbeats.
         * <p>
         * This API is in DRAFT state and is subject to change at ANY time until declared stable.
         * <p>
         * If this option is set, every ping message sent for heartbeat will contain this context.
         *
         * @return the context to be sent with ping messages. Empty array by default.
         */
        @Draft
        public byte[] getHeartbeatContext()
        {
            return (byte[]) base.getSocketOptx(zmq.ZMQ.ZMQ_HEARTBEAT_CONTEXT);
        }

        /**
         * The ZMQ_HANDSHAKE_IVL option shall set the maximum handshake interval for the specified socket.
         * Handshaking is the exchange of socket configuration information (socket type, identity, security)
         * that occurs when a connection is first opened, only for connection-oriented transports.
         * If handshaking does not complete within the configured time, the connection shall be closed.
         * The value 0 means no handshake time limit.
         *
         * @param maxHandshakeIvl the maximum handshake interval
         * @return true if the option was set, otherwise false
         * @see #getHandshakeIvl()
         */
        public boolean setHandshakeIvl(int maxHandshakeIvl)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HANDSHAKE_IVL, maxHandshakeIvl);
        }

        /**
         * The ZMQ_HEARTBEAT_IVL option shall set the interval
         * between sending ZMTP heartbeats for the specified socket.
         * If this option is set and is greater than 0,
         * then a PING ZMTP command will be sent every ZMQ_HEARTBEAT_IVL milliseconds.
         *
         * @param heartbeatIvl heartbeat interval in milliseconds
         * @return true if the option was set, otherwise false
         */
        public boolean setHeartbeatIvl(int heartbeatIvl)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_IVL, heartbeatIvl);
        }

        /**
         * The ZMQ_HEARTBEAT_TIMEOUT option shall set
         * how long to wait before timing-out a connection
         * after sending a PING ZMTP command and not receiving any traffic.
         * This option is only valid if ZMQ_HEARTBEAT_IVL is also set,
         * and is greater than 0. The connection will time out
         * if there is no traffic received after sending the PING command,
         * but the received traffic does not have to be a PONG command
         * - any received traffic will cancel the timeout.
         *
         * @param heartbeatTimeout heartbeat timeout in milliseconds
         * @return true if the option was set, otherwise false
         */
        public boolean setHeartbeatTimeout(int heartbeatTimeout)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_TIMEOUT, heartbeatTimeout);
        }

        /**
         * The ZMQ_HEARTBEAT_TTL option shall set the timeout
         * on the remote peer for ZMTP heartbeats.
         * If this option is greater than 0,
         * the remote side shall time out the connection
         * if it does not receive any more traffic within the TTL period.
         * This option does not have any effect if ZMQ_HEARTBEAT_IVL is not set or is 0.
         * Internally, this value is rounded down to the nearest decisecond,
         * any value less than 100 will have no effect.
         *
         * @param heartbeatTtl heartbeat time-to-live in milliseconds
         * @return true if the option was set, otherwise false
         */
        public boolean setHeartbeatTtl(int heartbeatTtl)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_TTL, heartbeatTtl);
        }

        /**
         * The ZMQ_HEARTBEAT_CONTEXT option shall set the ping context
         * of the peer for ZMTP heartbeats.
         * <p>
         * This API is in DRAFT state and is subject to change at ANY time until declared stable.
         * <p>
         * If this option is set, every ping message sent for heartbeat will contain this context.
         *
         * @param pingContext the context to be sent with ping messages.
         * @return true if the option was set, otherwise false
         */
        @Draft
        public boolean setHeartbeatContext(byte[] pingContext)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HEARTBEAT_CONTEXT, pingContext);
        }

        /**
         * Retrieve the IP_TOS option for the socket.
         *
         * @return the value of the Type-Of-Service set for the socket.
         * @see #setTos(int)
         */
        public int getTos()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TOS);
        }

        /**
         * Sets the ToS fields (Differentiated services (DS)
         * and Explicit Congestion Notification (ECN) field of the IP header.
         * The ToS field is typically used to specify a packets priority.
         * The availability of this option is dependent on intermediate network equipment
         * that inspect the ToS field andprovide a path for low-delay, high-throughput, highly-reliable service, etc.
         *
         * @return true if the option was set, otherwise false.
         * @see #getTos()
         */
        public boolean setTos(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TOS, value);
        }

        /**
         * The ZMQ_RECONNECT_IVL_MAX option shall retrieve the maximum reconnection interval for the specified socket.
         * This is the maximum period ØMQ shall wait between attempts to reconnect.
         * On each reconnect attempt, the previous interval shall be doubled untill ZMQ_RECONNECT_IVL_MAX is reached.
         * This allows for exponential backoff strategy.
         * Default value means no exponential backoff is performed and reconnect interval calculations are only based on ZMQ_RECONNECT_IVL.
         *
         * @return the reconnectIVLMax.
         * @see #setReconnectIVLMax(int)
         */
        public int getReconnectIVLMax()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX);
        }

        /**
         * The ZMQ_RECONNECT_IVL_MAX option shall set the maximum reconnection interval for the specified socket.
         * This is the maximum period ØMQ shall wait between attempts to reconnect.
         * On each reconnect attempt, the previous interval shall be doubled until ZMQ_RECONNECT_IVL_MAX is reached.
         * This allows for exponential backoff strategy.
         * Default value means no exponential backoff is performed and reconnect interval calculations are only based on ZMQ_RECONNECT_IVL.
         *
         * @return true if the option was set, otherwise false
         * @see #getReconnectIVLMax()
         * @deprecated this option uses integer range, use {@link #setReconnectIVLMax(int)} instead
         */
        @Deprecated
        public boolean setReconnectIVLMax(long value)
        {
            return setReconnectIVLMax(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_RECONNECT_IVL_MAX option shall set the maximum reconnection interval for the specified socket.
         * This is the maximum period ØMQ shall wait between attempts to reconnect.
         * On each reconnect attempt, the previous interval shall be doubled until ZMQ_RECONNECT_IVL_MAX is reached.
         * This allows for exponential backoff strategy.
         * Default value means no exponential backoff is performed and reconnect interval calculations are only based on ZMQ_RECONNECT_IVL.
         *
         * @return true if the option was set, otherwise false
         * @see #getReconnectIVLMax()
         */
        public boolean setReconnectIVLMax(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX, value);
        }

        /**
         * The option shall retrieve limit for the inbound messages.
         * If a peer sends a message larger than ZMQ_MAXMSGSIZE it is disconnected.
         * Value of -1 means no limit.
         *
         * @return the maxMsgSize.
         * @see #setMaxMsgSize(long)
         */
        public long getMaxMsgSize()
        {
            return (Long) base.getSocketOptx(zmq.ZMQ.ZMQ_MAXMSGSIZE);
        }

        /**
         * Limits the size of the inbound message.
         * If a peer sends a message larger than ZMQ_MAXMSGSIZE it is disconnected.
         * Value of -1 means no limit.
         *
         * @return true if the option was set, otherwise false
         * @see #getMaxMsgSize()
         */
        public boolean setMaxMsgSize(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_MAXMSGSIZE, value);
        }

        /**
         * The ZMQ_SNDHWM option shall return the high water mark for outbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         *
         * @return the SndHWM.
         * @see #setSndHWM(int)
         */
        public int getSndHWM()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_SNDHWM);
        }

        /**
         * The ZMQ_SNDHWM option shall set the high water mark for outbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         * <p>
         * CAUTION: ØMQ does not guarantee that the socket will accept as many as ZMQ_SNDHWM messages,
         * and the actual limit may be as much as 60-70% lower depending on the flow of messages on the socket.
         *
         * @return true if the option was set, otherwise false.
         * @see #getSndHWM()
         * @deprecated this option uses integer range, use {@link #setSndHWM(int)} instead
         */
        @Deprecated
        public boolean setSndHWM(long value)
        {
            return setSndHWM(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_SNDHWM option shall set the high water mark for outbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         * <p>
         * CAUTION: ØMQ does not guarantee that the socket will accept as many as ZMQ_SNDHWM messages,
         * and the actual limit may be as much as 60-70% lower depending on the flow of messages on the socket.
         *
         * @param value
         * @return true if the option was set, otherwise false.
         * @see #getSndHWM()
         */
        public boolean setSndHWM(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SNDHWM, value);
        }

        /**
         * The ZMQ_RCVHWM option shall return the high water mark for inbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         *
         * @return the recvHWM period.
         * @see #setRcvHWM(int)
         */
        public int getRcvHWM()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RCVHWM);
        }

        /**
         * The ZMQ_RCVHWM option shall set the high water mark for inbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         *
         * @return true if the option was set, otherwise false
         * @see #getRcvHWM()
         * @deprecated this option uses integer range, use {@link #setRcvHWM(int)} instead
         */
        @Deprecated
        public boolean setRcvHWM(long value)
        {
            return setRcvHWM(Long.valueOf(value).intValue());
        }

        /**
         * The ZMQ_RCVHWM option shall set the high water mark for inbound messages on the specified socket.
         * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ
         * shall queue in memory for any single peer that the specified socket is communicating with.
         * A value of zero means no limit.
         * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
         * ØMQ shall take appropriate action such as blocking or dropping sent messages.
         * Refer to the individual socket descriptions in zmq_socket(3) for details on the exact action taken for each socket type.
         *
         * @param value
         * @return true if the option was set, otherwise false.
         * @see #getRcvHWM()
         */
        public boolean setRcvHWM(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_RCVHWM, value);
        }

        /**
         * @return the High Water Mark.
         * @see #setHWM(int)
         */
        @Deprecated
        public int getHWM()
        {
            return -1;
        }

        /**
         * The 'ZMQ_HWM' option shall set the high water mark for the specified 'socket'. The high
         * water mark is a hard limit on the maximum number of outstanding messages 0MQ shall queue
         * in memory for any single peer that the specified 'socket' is communicating with.
         * <p>
         * If this limit has been reached the socket shall enter an exceptional state and depending
         * on the socket type, 0MQ shall take appropriate action such as blocking or dropping sent
         * messages. Refer to the individual socket descriptions in the man page of zmq_socket[3] for
         * details on the exact action taken for each socket type.
         *
         * @param hwm the number of messages to queue.
         * @return true if the option was set, otherwise false.
         * @deprecated this option uses integer range, use {@link #setHWM(int)} instead
         */
        @Deprecated
        public boolean setHWM(long hwm)
        {
            boolean set = true;
            set |= setSndHWM(hwm);
            set |= setRcvHWM(hwm);
            return set;
        }

        /**
         * The 'ZMQ_HWM' option shall set the high water mark for the specified 'socket'. The high
         * water mark is a hard limit on the maximum number of outstanding messages 0MQ shall queue
         * in memory for any single peer that the specified 'socket' is communicating with.
         * <p>
         * If this limit has been reached the socket shall enter an exceptional state and depending
         * on the socket type, 0MQ shall take appropriate action such as blocking or dropping sent
         * messages. Refer to the individual socket descriptions in the man page of zmq_socket[3] for
         * details on the exact action taken for each socket type.
         *
         * @param hwm the number of messages to queue.
         * @return true if the option was set, otherwise false
         */
        public boolean setHWM(int hwm)
        {
            boolean set = false;
            set |= setSndHWM(hwm);
            set |= setRcvHWM(hwm);
            return set;
        }

        /**
         * @return the number of messages to swap at most.
         * @see #setSwap(long)
         */
        @Deprecated
        public long getSwap()
        {
            // not support at zeromq 3
            return -1L;
        }

        /**
         * If set, a socket shall keep only one message in its inbound/outbound queue,
         * this message being the last message received/the last message to be sent.
         * Ignores ZMQ_RCVHWM and ZMQ_SNDHWM options.
         * Does not support multi-part messages, in particular,
         * only one part of it is kept in the socket internal queue.
         *
         * @param conflate true to keep only one message, false for standard behaviour.
         * @return true if the option was set, otherwise false.
         * @see #isConflate()
         */
        public boolean setConflate(boolean conflate)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CONFLATE, conflate);
        }

        /**
         * If in conflate mode, a socket shall keep only one message in its inbound/outbound queue,
         * this message being the last message received/the last message to be sent.
         * Ignores ZMQ_RCVHWM and ZMQ_SNDHWM options.
         * Does not support multi-part messages, in particular,
         * only one part of it is kept in the socket internal queue.
         *
         * @return true to keep only one message, false for standard behaviour.
         * @see #setConflate(boolean)
         */
        public boolean isConflate()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_CONFLATE) != 0;
        }

        /**
         * If in conflate mode, a socket shall keep only one message in its inbound/outbound queue,
         * this message being the last message received/the last message to be sent.
         * Ignores ZMQ_RCVHWM and ZMQ_SNDHWM options.
         * Does not support multi-part messages, in particular,
         * only one part of it is kept in the socket internal queue.
         *
         * @return true to keep only one message, false for standard behaviour.
         * @see #setConflate(boolean)
         */
        public boolean getConflate()
        {
            return isConflate();
        }

        /**
         * Get the Swap. The 'ZMQ_SWAP' option shall set the disk offload (swap) size for the
         * specified 'socket'. A socket which has 'ZMQ_SWAP' set to a non-zero value may exceed its
         * high water mark; in this case outstanding messages shall be offloaded to storage on disk
         * rather than held in memory.
         *
         * @param value The value of 'ZMQ_SWAP' defines the maximum size of the swap space in bytes.
         */
        @Deprecated
        public boolean setSwap(long value)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * @return the affinity.
         * @see #setAffinity(long)
         */
        public long getAffinity()
        {
            return (Long) base.getSocketOptx(zmq.ZMQ.ZMQ_AFFINITY);
        }

        /**
         * Get the Affinity. The 'ZMQ_AFFINITY' option shall set the I/O thread affinity for newly
         * created connections on the specified 'socket'.
         * <p>
         * Affinity determines which threads from the 0MQ I/O thread pool associated with the
         * socket's _context_ shall handle newly created connections. A value of zero specifies no
         * affinity, meaning that work shall be distributed fairly among all 0MQ I/O threads in the
         * thread pool. For non-zero values, the lowest bit corresponds to thread 1, second lowest
         * bit to thread 2 and so on. For example, a value of 3 specifies that subsequent
         * connections on 'socket' shall be handled exclusively by I/O threads 1 and 2.
         * <p>
         * See also  in the man page of init[3] for details on allocating the number of I/O threads for a
         * specific _context_.
         *
         * @param value the io_thread affinity.
         * @return true if the option was set, otherwise false
         */
        public boolean setAffinity(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_AFFINITY, value);
        }

        /**
         * @return the Identitiy.
         * @see #setIdentity(byte[])
         */
        public byte[] getIdentity()
        {
            return (byte[]) base.getSocketOptx(zmq.ZMQ.ZMQ_IDENTITY);
        }

        /**
         * The 'ZMQ_IDENTITY' option shall set the identity of the specified 'socket'. Socket
         * identity determines if existing 0MQ infastructure (_message queues_, _forwarding
         * devices_) shall be identified with a specific application and persist across multiple
         * runs of the application.
         * <p>
         * If the socket has no identity, each run of an application is completely separate from
         * other runs. However, with identity set the socket shall re-use any existing 0MQ
         * infrastructure configured by the previous run(s). Thus the application may receive
         * messages that were sent in the meantime, _message queue_ limits shall be shared with
         * previous run(s) and so on.
         * <p>
         * Identity should be at least one byte and at most 255 bytes long. Identities starting with
         * binary zero are reserved for use by 0MQ infrastructure.
         *
         * @param identity
         * @return true if the option was set, otherwise false
         */
        public boolean setIdentity(byte[] identity)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_IDENTITY, identity);
        }

        /**
         * @return the Rate.
         * @see #setRate(long)
         */
        public long getRate()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RATE);
        }

        /**
         * The 'ZMQ_RATE' option shall set the maximum send or receive data rate for multicast
         * transports such as in the man page of zmq_pgm[7] using the specified 'socket'.
         *
         * @param value maximum send or receive data rate for multicast, default 100
         * @return true if the option was set, otherwise false
         */
        public boolean setRate(long value)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * The ZMQ_RECOVERY_IVL option shall retrieve the recovery interval for multicast transports
         * using the specified socket. The recovery interval determines the maximum time in milliseconds
         * that a receiver can be absent from a multicast group before unrecoverable data loss will occur.
         *
         * @return the RecoveryIntervall.
         * @see #setRecoveryInterval(long)
         */
        public long getRecoveryInterval()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RECOVERY_IVL);
        }

        /**
         * The 'ZMQ_RECOVERY_IVL' option shall set the recovery interval for multicast transports
         * using the specified 'socket'. The recovery interval determines the maximum time in
         * seconds that a receiver can be absent from a multicast group before unrecoverable data
         * loss will occur.
         * <p>
         * CAUTION: Exercise care when setting large recovery intervals as the data needed for
         * recovery will be held in memory. For example, a 1 minute recovery interval at a data rate
         * of 1Gbps requires a 7GB in-memory buffer. {Purpose of this Method}
         *
         * @param value recovery interval for multicast in milliseconds, default 10000
         * @return true if the option was set, otherwise false.
         * @see #getRecoveryInterval()
         */
        public boolean setRecoveryInterval(long value)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * The default behavior of REQ sockets is to rely on the ordering of messages
         * to match requests and responses and that is usually sufficient.
         * When this option is set to true, the REQ socket will prefix outgoing messages
         * with an extra frame containing a request id.
         * That means the full message is (request id, identity, 0, user frames…).
         * The REQ socket will discard all incoming messages that don't begin with these two frames.
         * See also ZMQ_REQ_RELAXED.
         *
         * @param correlate Whether to enable outgoing request ids.
         * @return true if the option was set, otherwise false
         * @see #getReqCorrelate()
         */
        public boolean setReqCorrelate(boolean correlate)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_REQ_CORRELATE, correlate);
        }

        /**
         * The default behavior of REQ sockets is to rely on the ordering of messages
         * to match requests and responses and that is usually sufficient.
         * When this option is set to true, the REQ socket will prefix outgoing messages
         * with an extra frame containing a request id.
         * That means the full message is (request id, identity, 0, user frames…).
         * The REQ socket will discard all incoming messages that don't begin with these two frames.
         *
         * @return state of the ZMQ_REQ_CORRELATE option.
         * @see #setReqCorrelate(boolean)
         */
        @Deprecated
        public boolean getReqCorrelate()
        {
            throw new UnsupportedOperationException();
        }

        /**
         * By default, a REQ socket does not allow initiating a new request with zmq_send(3)
         * until the reply to the previous one has been received.
         * When set to true, sending another message is allowed and has the effect of disconnecting
         * the underlying connection to the peer from which the reply was expected,
         * triggering a reconnection attempt on transports that support it.
         * The request-reply state machine is reset and a new request is sent to the next available peer.
         * If set to true, also enable ZMQ_REQ_CORRELATE to ensure correct matching of requests and replies.
         * Otherwise a late reply to an aborted request can be reported as the reply to the superseding request.
         *
         * @param relaxed
         * @return true if the option was set, otherwise false
         * @see #getReqRelaxed()
         */
        public boolean setReqRelaxed(boolean relaxed)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_REQ_RELAXED, relaxed);
        }

        /**
         * By default, a REQ socket does not allow initiating a new request with zmq_send(3)
         * until the reply to the previous one has been received.
         * When set to true, sending another message is allowed and has the effect of disconnecting
         * the underlying connection to the peer from which the reply was expected,
         * triggering a reconnection attempt on transports that support it.
         * The request-reply state machine is reset and a new request is sent to the next available peer.
         * If set to true, also enable ZMQ_REQ_CORRELATE to ensure correct matching of requests and replies.
         * Otherwise a late reply to an aborted request can be reported as the reply to the superseding request.
         *
         * @return state of the ZMQ_REQ_RELAXED option.
         * @see #setReqRelaxed(boolean)
         */
        @Deprecated
        public boolean getReqRelaxed()
        {
            throw new UnsupportedOperationException();
        }

        /**
         * @return the Multicast Loop.
         * @see #setMulticastLoop(boolean)
         */
        @Deprecated
        public boolean hasMulticastLoop()
        {
            return false;
        }

        /**
         * The 'ZMQ_MCAST_LOOP' option shall control whether data sent via multicast transports
         * using the specified 'socket' can also be received by the sending host via loopback. A
         * value of zero disables the loopback functionality, while the default value of 1 enables
         * the loopback functionality. Leaving multicast loopback enabled when it is not required
         * can have a negative impact on performance. Where possible, disable 'ZMQ_MCAST_LOOP' in
         * production environments.
         *
         * @param multicastLoop
         */
        @Deprecated
        public boolean setMulticastLoop(boolean multicastLoop)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * @return the Multicast Hops.
         * @see #setMulticastHops(long)
         */
        public long getMulticastHops()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_MULTICAST_HOPS);
        }

        /**
         * Sets the time-to-live field in every multicast packet sent from this socket.
         * The default is 1 which means that the multicast packets don't leave the local
         * network.
         *
         * @param value time-to-live field in every multicast packet, default 1
         */
        public boolean setMulticastHops(long value)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * Retrieve the timeout for recv operation on the socket.
         * If the value is 0, recv will return immediately,
         * with null if there is no message to receive.
         * If the value is -1, it will block until a message is available.
         * For all other values, it will wait for a message for that amount of time
         * before returning with a null and an EAGAIN error.
         *
         * @return the Receive Timeout  in milliseconds.
         * @see #setReceiveTimeOut(int)
         */
        public int getReceiveTimeOut()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RCVTIMEO);
        }

        /**
         * Sets the timeout for receive operation on the socket. If the value is 0, recv
         * will return immediately, with null if there is no message to receive.
         * If the value is -1, it will block until a message is available. For all other
         * values, it will wait for a message for that amount of time before returning with
         * a null and an EAGAIN error.
         *
         * @param value Timeout for receive operation in milliseconds. Default -1 (infinite)
         * @return true if the option was set, otherwise false.
         * @see #getReceiveTimeOut()
         */
        public boolean setReceiveTimeOut(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_RCVTIMEO, value);
        }

        /**
         * Retrieve the timeout for send operation on the socket.
         * If the value is 0, send will return immediately, with a false and an EAGAIN error if the message cannot be sent.
         * If the value is -1, it will block until the message is sent.
         * For all other values, it will try to send the message for that amount of time before returning with false and an EAGAIN error.
         *
         * @return the Send Timeout in milliseconds.
         * @see #setSendTimeOut(int)
         */
        public int getSendTimeOut()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_SNDTIMEO);
        }

        /**
         * Sets the timeout for send operation on the socket. If the value is 0, send
         * will return immediately, with a false if the message cannot be sent.
         * If the value is -1, it will block until the message is sent. For all other
         * values, it will try to send the message for that amount of time before
         * returning with false and an EAGAIN error.
         *
         * @param value Timeout for send operation in milliseconds. Default -1 (infinite)
         * @return true if the option was set, otherwise false.
         * @see #getSendTimeOut()
         */
        public boolean setSendTimeOut(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SNDTIMEO, value);
        }

        /**
         * Override SO_KEEPALIVE socket option (where supported by OS) to enable keep-alive packets for a socket
         * connection. Possible values are -1, 0, 1. The default value -1 will skip all overrides and do the OS default.
         *
         * @param value The value of 'ZMQ_TCP_KEEPALIVE' to turn TCP keepalives on (1) or off (0).
         * @return true if the option was set, otherwise false.
         */
        @Deprecated
        public boolean setTCPKeepAlive(long value)
        {
            return setTCPKeepAlive(Long.valueOf(value).intValue());
        }

        /**
         * @return the keep alive setting.
         * @see #setTCPKeepAlive(long)
         */
        @Deprecated
        public long getTCPKeepAliveSetting()
        {
            return getTCPKeepAlive();
        }

        /**
         * Override TCP_KEEPCNT socket option (where supported by OS). The default value -1 will skip all overrides and
         * do the OS default.
         *
         * @param value The value of 'ZMQ_TCP_KEEPALIVE_CNT' defines the number of keepalives before death.
         * @return true if the option was set, otherwise false.
         */
        public boolean setTCPKeepAliveCount(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_CNT, Long.valueOf(value).intValue());
        }

        /**
         * @return the keep alive count.
         * @see #setTCPKeepAliveCount(long)
         */
        public long getTCPKeepAliveCount()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_CNT);
        }

        /**
         * Override TCP_KEEPINTVL socket option (where supported by OS). The default value -1 will skip all overrides
         * and do the OS default.
         *
         * @param value The value of 'ZMQ_TCP_KEEPALIVE_INTVL' defines the interval between keepalives. Unit is OS
         *              dependent.
         * @return true if the option was set, otherwise false.
         */
        public boolean setTCPKeepAliveInterval(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_INTVL, Long.valueOf(value).intValue());
        }

        /**
         * @return the keep alive interval.
         * @see #setTCPKeepAliveInterval(long)
         */
        public long getTCPKeepAliveInterval()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_INTVL);
        }

        /**
         * Override TCP_KEEPCNT (or TCP_KEEPALIVE on some OS) socket option (where supported by OS). The default value
         * -1 will skip all overrides and do the OS default.
         *
         * @param value The value of 'ZMQ_TCP_KEEPALIVE_IDLE' defines the interval between the last data packet sent
         *              over the socket and the first keepalive probe. Unit is OS dependent.
         * @return true if the option was set, otherwise false
         */
        public boolean setTCPKeepAliveIdle(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_IDLE, Long.valueOf(value).intValue());
        }

        /**
         * @return the keep alive idle value.
         * @see #setTCPKeepAliveIdle(long)
         */
        public long getTCPKeepAliveIdle()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_IDLE);
        }

        /**
         * The ZMQ_SNDBUF option shall retrieve the underlying kernel transmit buffer size for the specified socket.
         * For details refer to your operating system documentation for the SO_SNDBUF socket option.
         *
         * @return the kernel send buffer size.
         * @see #setSendBufferSize(int)
         */
        public int getSendBufferSize()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_SNDBUF);
        }

        /**
         * The 'ZMQ_SNDBUF' option shall set the underlying kernel transmit buffer size for the
         * 'socket' to the specified size in bytes. A value of zero means leave the OS default
         * unchanged. For details please refer to your operating system documentation for the
         * 'SO_SNDBUF' socket option.
         *
         * @param value underlying kernel transmit buffer size for the 'socket' in bytes
         *              A value of zero means leave the OS default unchanged.
         * @return true if the option was set, otherwise false
         * @see #getSendBufferSize()
         * @deprecated this option uses integer range, use {@link #setSendBufferSize(int)} instead
         */
        @Deprecated
        public boolean setSendBufferSize(long value)
        {
            return setSendBufferSize(Long.valueOf(value).intValue());
        }

        /**
         * The 'ZMQ_SNDBUF' option shall set the underlying kernel transmit buffer size for the
         * 'socket' to the specified size in bytes. A value of zero means leave the OS default
         * unchanged. For details please refer to your operating system documentation for the
         * 'SO_SNDBUF' socket option.
         *
         * @param value underlying kernel transmit buffer size for the 'socket' in bytes
         *              A value of zero means leave the OS default unchanged.
         * @return true if the option was set, otherwise false
         * @see #getSendBufferSize()
         */
        public boolean setSendBufferSize(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SNDBUF, value);
        }

        /**
         * The ZMQ_RCVBUF option shall retrieve the underlying kernel receive buffer size for the specified socket.
         * For details refer to your operating system documentation for the SO_RCVBUF socket option.
         *
         * @return the kernel receive buffer size.
         * @see #setReceiveBufferSize(int)
         */
        public int getReceiveBufferSize()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RCVBUF);
        }

        /**
         * The 'ZMQ_RCVBUF' option shall set the underlying kernel receive buffer size for the
         * 'socket' to the specified size in bytes.
         * For details refer to your operating system documentation for the 'SO_RCVBUF'
         * socket option.
         *
         * @param value Underlying kernel receive buffer size for the 'socket' in bytes.
         *              A value of zero means leave the OS default unchanged.
         * @return true if the option was set, otherwise false
         * @see #getReceiveBufferSize()
         * @deprecated this option uses integer range, use {@link #setReceiveBufferSize(int)} instead
         */
        @Deprecated
        public boolean setReceiveBufferSize(long value)
        {
            return setReceiveBufferSize(Long.valueOf(value).intValue());
        }

        /**
         * The 'ZMQ_RCVBUF' option shall set the underlying kernel receive buffer size for the
         * 'socket' to the specified size in bytes.
         * For details refer to your operating system documentation for the 'SO_RCVBUF'
         * socket option.
         *
         * @param value Underlying kernel receive buffer size for the 'socket' in bytes.
         *              A value of zero means leave the OS default unchanged.
         * @return true if the option was set, otherwise false
         * @see #getReceiveBufferSize()
         */
        public boolean setReceiveBufferSize(int value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_RCVBUF, value);
        }

        /**
         * The 'ZMQ_RCVMORE' option shall return a boolean value indicating if the multi-part
         * message currently being read from the specified 'socket' has more message parts to
         * follow. If there are no message parts to follow or if the message currently being read is
         * not a multi-part message a value of zero shall be returned. Otherwise, a value of 1 shall
         * be returned.
         *
         * @return true if there are more messages to receive.
         */
        public boolean hasReceiveMore()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_RCVMORE) == 1;
        }

        /**
         * The 'ZMQ_FD' option shall retrieve file descriptor associated with the 0MQ
         * socket. The descriptor can be used to integrate 0MQ socket into an existing
         * event loop. It should never be used for anything else than polling -- such as
         * reading or writing. The descriptor signals edge-triggered IN event when
         * something has happened within the 0MQ socket. It does not necessarily mean that
         * the messages can be read or written. Check ZMQ_EVENTS option to find out whether
         * the 0MQ socket is readable or writeable.
         *
         * @return the underlying file descriptor.
         */
        public SelectableChannel getFD()
        {
            return (SelectableChannel) base.getSocketOptx(zmq.ZMQ.ZMQ_FD);
        }

        /**
         * The 'ZMQ_EVENTS' option shall retrieve event flags for the specified socket.
         * If a message can be read from the socket ZMQ_POLLIN flag is set. If message can
         * be written to the socket ZMQ_POLLOUT flag is set.
         *
         * @return the mask of outstanding events.
         */
        public int getEvents()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_EVENTS);
        }

        /**
         * The 'ZMQ_SUBSCRIBE' option shall establish a new message filter on a 'ZMQ_SUB' socket.
         * Newly created 'ZMQ_SUB' sockets shall filter out all incoming messages, therefore you
         * should call this option to establish an initial message filter.
         * <p>
         * An empty 'option_value' of length zero shall subscribe to all incoming messages. A
         * non-empty 'option_value' shall subscribe to all messages beginning with the specified
         * prefix. Mutiple filters may be attached to a single 'ZMQ_SUB' socket, in which case a
         * message shall be accepted if it matches at least one filter.
         *
         * @param topic
         * @return true if the option was set, otherwise false
         */
        public boolean subscribe(byte[] topic)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SUBSCRIBE, topic);
        }

        /**
         * The 'ZMQ_SUBSCRIBE' option shall establish a new message filter on a 'ZMQ_SUB' socket.
         * Newly created 'ZMQ_SUB' sockets shall filter out all incoming messages, therefore you
         * should call this option to establish an initial message filter.
         * <p>
         * An empty 'option_value' of length zero shall subscribe to all incoming messages. A
         * non-empty 'option_value' shall subscribe to all messages beginning with the specified
         * prefix. Mutiple filters may be attached to a single 'ZMQ_SUB' socket, in which case a
         * message shall be accepted if it matches at least one filter.
         *
         * @param topic
         * @return true if the option was set, otherwise false
         */
        public boolean subscribe(String topic)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SUBSCRIBE, topic);
        }

        /**
         * The 'ZMQ_UNSUBSCRIBE' option shall remove an existing message filter on a 'ZMQ_SUB'
         * socket. The filter specified must match an existing filter previously established with
         * the 'ZMQ_SUBSCRIBE' option. If the socket has several instances of the same filter
         * attached the 'ZMQ_UNSUBSCRIBE' option shall remove only one instance, leaving the rest in
         * place and functional.
         *
         * @param topic
         * @return true if the option was set, otherwise false
         */
        public boolean unsubscribe(byte[] topic)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_UNSUBSCRIBE, topic);
        }

        /**
         * The 'ZMQ_UNSUBSCRIBE' option shall remove an existing message filter on a 'ZMQ_SUB'
         * socket. The filter specified must match an existing filter previously established with
         * the 'ZMQ_SUBSCRIBE' option. If the socket has several instances of the same filter
         * attached the 'ZMQ_UNSUBSCRIBE' option shall remove only one instance, leaving the rest in
         * place and functional.
         *
         * @param topic
         * @return true if the option was set, otherwise false
         */
        public boolean unsubscribe(String topic)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_UNSUBSCRIBE, topic);
        }

        /**
         * Joins a group.
         * Opposite action is {@link Socket#leave(String)}
         * @param group the name of the group to join. Limited to 16 characters.
         * @return true if the group was no already joined, otherwise false.
         */
        public boolean join(String group)
        {
            assert ("DISH".equals(base.typeString())) : "Only DISH sockets can join a group";
            return base.join(group);
        }

        /**
         * Leaves a group.
         * Opposite action is {@link Socket#join(String)}
         * @param group the name of the group to leave. Limited to 16 characters.
         * @return false if the group was not joined before, otherwise true.
         */
        public boolean leave(String group)
        {
            assert ("DISH".equals(base.typeString())) : "Only DISH sockets can leave a group";
            return base.leave(group);
        }

        /**
         * Set custom Encoder
         *
         * @param cls
         * @return true if the option was set, otherwise false
         */
        @Deprecated
        public boolean setEncoder(Class<? extends IEncoder> cls)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ENCODER, cls);
        }

        /**
         * Set custom Decoder
         *
         * @param cls
         * @return true if the option was set, otherwise false
         */
        @Deprecated
        public boolean setDecoder(Class<? extends IDecoder> cls)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_DECODER, cls);
        }

        /**
         * Sets the limit threshold where messages of a given size will be allocated using Direct ByteBuffer.
         * It means that after this limit, there will be a slight penalty cost at the creation,
         * but the subsequent operations will be faster.
         * Set to 0 or negative to disable the threshold mechanism.
         *
         * @param threshold the threshold to set for the size limit of messages. 0 or negative to disable this system.
         * @return true if the option was set, otherwise false.
         */
        public boolean setMsgAllocationHeapThreshold(int threshold)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD, threshold);
        }

        /**
         * Gets the limit threshold where messages of a given size will be allocated using Direct ByteBuffer.
         * It means that after this limit, there will be a slight penalty cost at the creation,
         * but the subsequent operations will be faster.
         *
         * @return the threshold
         */
        public int getMsgAllocationHeapThreshold()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD);
        }

        /**
         * Sets a custom message allocator.
         *
         * @param allocator the custom allocator.
         * @return true if the option was set, otherwise false.
         */
        public boolean setMsgAllocator(MsgAllocator allocator)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_MSG_ALLOCATOR, allocator);
        }

        /**
         * Set a custom {@link java.nio.channels.spi.SelectorProvider} chooser.
         *
         * @param chooser the custom chooser.
         * @return true if the option was set, otherwise false.
         */
        public boolean setSelectorChooser(SelectorProviderChooser chooser)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_SELECTOR_PROVIDERCHOOSER, chooser);
        }

        /**
         * Return the custom {@link java.nio.channels.spi.SelectorProvider} chooser.
         *
         * @return the {@link java.nio.channels.spi.SelectorProvider} chooser.
         */
        public SelectorProviderChooser getSelectorProviderChooser()
        {
            return (SelectorProviderChooser) base.getSocketOptx(zmq.ZMQ.ZMQ_SELECTOR_PROVIDERCHOOSER);
        }

        /**
         * The ZMQ_CONNECT_RID option sets the peer id of the next host connected via the connect() call,
         * and immediately readies that connection for data transfer with the named id.
         * This option applies only to the first subsequent call to connect(),
         * calls thereafter use default connection behavior.
         * Typical use is to set this socket option ahead of each connect() attempt to a new host.
         * Each connection MUST be assigned a unique name. Assigning a name that is already in use is not allowed.
         * Useful when connecting ROUTER to ROUTER, or STREAM to STREAM, as it allows for immediate sending to peers.
         * Outbound id framing requirements for ROUTER and STREAM sockets apply.
         * The peer id should be from 1 to 255 bytes long and MAY NOT start with binary zero.
         *
         * @param rid the peer id of the next host.
         * @return true if the option was set, otherwise false.
         */
        public boolean setConnectRid(String rid)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CONNECT_RID, rid);
        }

        /**
         * The ZMQ_CONNECT_RID option sets the peer id of the next host connected via the connect() call,
         * and immediately readies that connection for data transfer with the named id.
         * This option applies only to the first subsequent call to connect(),
         * calls thereafter use default connection behavior.
         * Typical use is to set this socket option ahead of each connect() attempt to a new host.
         * Each connection MUST be assigned a unique name. Assigning a name that is already in use is not allowed.
         * Useful when connecting ROUTER to ROUTER, or STREAM to STREAM, as it allows for immediate sending to peers.
         * Outbound id framing requirements for ROUTER and STREAM sockets apply.
         * The peer id should be from 1 to 255 bytes long and MAY NOT start with binary zero.
         *
         * @param rid the peer id of the next host.
         * @return true if the option was set, otherwise false.
         */
        public boolean setConnectRid(byte[] rid)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CONNECT_RID, rid);
        }

        /**
         * Sets the raw mode on the ROUTER, when set to true.
         * When the ROUTER socket is in raw mode, and when using the tcp:// transport,
         * it will read and write TCP data without ØMQ framing.
         * This lets ØMQ applications talk to non-ØMQ applications.
         * When using raw mode, you cannot set explicit identities,
         * and the ZMQ_SNDMORE flag is ignored when sending data messages.
         * In raw mode you can close a specific connection by sending it a zero-length message (following the identity frame).
         *
         * @param raw true to set the raw mode on the ROUTER.
         * @return true if the option was set, otherwise false.
         */
        public boolean setRouterRaw(boolean raw)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ROUTER_RAW, raw);
        }

        /**
         * When set to true, the socket will automatically send
         * an empty message when a new connection is made or accepted.
         * You may set this on REQ, DEALER, or ROUTER sockets connected to a ROUTER socket.
         * The application must filter such empty messages.
         * The ZMQ_PROBE_ROUTER option in effect provides the ROUTER application with an event signaling the arrival of a new peer.
         *
         * @param probe true to send automatically an empty message when a new connection is made or accepted.
         * @return true if the option was set, otherwise false.
         */
        public boolean setProbeRouter(boolean probe)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_PROBE_ROUTER, probe);
        }

        /**
         * Sets the ROUTER socket behavior when an unroutable message is encountered.
         * A value of false is the default and discards the message silently
         * when it cannot be routed or the peers SNDHWM is reached.
         * A value of true returns an EHOSTUNREACH error code if the message cannot be routed
         * or EAGAIN error code if the SNDHWM is reached and ZMQ_DONTWAIT was used.
         * Without ZMQ_DONTWAIT it will block until the SNDTIMEO is reached or a spot in the send queue opens up.
         *
         * @param mandatory A value of false is the default and discards the message silently when it cannot be routed.
         *                  A value of true returns an EHOSTUNREACH error code if the message cannot be routed.
         * @return true if the option was set, otherwise false.
         */
        public boolean setRouterMandatory(boolean mandatory)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ROUTER_MANDATORY, mandatory);
        }

        /**
         * If two clients use the same identity when connecting to a ROUTER,
         * the results shall depend on the ZMQ_ROUTER_HANDOVER option setting.
         * If that is not set (or set to the default of false),
         * the ROUTER socket shall reject clients trying to connect with an already-used identity.
         * If that option is set to true, the ROUTER socket shall hand-over the connection to the new client and disconnect the existing one.
         *
         * @param handover A value of false, (default) the ROUTER socket shall reject clients trying to connect with an already-used identity
         *                 A value of true, the ROUTER socket shall hand-over the connection to the new client and disconnect the existing one
         * @return true if the option was set, otherwise false.
         */
        public boolean setRouterHandover(boolean handover)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ROUTER_HANDOVER, handover);
        }

        /**
         * Sets the XPUB socket behavior on new subscriptions and unsubscriptions.
         *
         * @param verbose A value of false is the default and passes only new subscription messages to upstream.
         *                A value of true passes all subscription messages upstream.
         * @return true if the option was set, otherwise false.
         */
        public boolean setXpubVerbose(boolean verbose)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_XPUB_VERBOSE, verbose);
        }

        /**
         * Sets the XPUB socket behaviour to return error EAGAIN if SENDHWM is reached and the message could not be send.
         * A value of false is the default and drops the message silently when the peers SNDHWM is reached.
         * A value of true returns an EAGAIN error code if the SNDHWM is reached and ZMQ_DONTWAIT was used.
         *
         * @param noDrop
         * @return true if the option was set, otherwise false.
         */
        public boolean setXpubNoDrop(boolean noDrop)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_XPUB_NODROP, noDrop);
        }

        public boolean setXpubManual(boolean manual)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_XPUB_MANUAL, manual);
        }

        public boolean setXpubVerboser(boolean verboser)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_XPUB_VERBOSER, verboser);
        }

        /**
         * @return the IPV4ONLY
         * @see #setIPv4Only (boolean)
         * @deprecated use {@link #isIPv6()} instead (inverted logic: ipv4 = true &lt;==&gt; ipv6 = false)
         */
        @Deprecated
        public boolean getIPv4Only()
        {
            return !isIPv6();
        }

        /**
         * Retrieve the IPv6 option for the socket.
         * A value of true means IPv6 is enabled on the socket,
         * while false means the socket will use only IPv4.
         * When IPv6 is enabled the socket will connect to,
         * or accept connections from, both IPv4 and IPv6 hosts.
         *
         * @return the IPV6 configuration.
         * @see #setIPv6 (boolean)
         */
        public boolean isIPv6()
        {
            return (Boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_IPV6);
        }

        /**
         * Retrieve the IPv6 option for the socket.
         * A value of true means IPv6 is enabled on the socket,
         * while false means the socket will use only IPv4.
         * When IPv6 is enabled the socket will connect to,
         * or accept connections from, both IPv4 and IPv6 hosts.
         *
         * @return the IPV6 configuration.
         * @see #setIPv6 (boolean)
         */
        public boolean getIPv6()
        {
            return isIPv6();
        }

        /**
         * The 'ZMQ_IPV4ONLY' option shall set the underlying native socket type.
         * An IPv6 socket lets applications connect to and accept connections from both IPv4 and IPv6 hosts.
         *
         * @param v4only A value of true will use IPv4 sockets, while the value of false will use IPv6 sockets
         * @return true if the option was set, otherwise false
         * @deprecated use {@link #setIPv6(boolean)} instead (inverted logic: ipv4 = true &lt;==&gt; ipv6 = false)
         */
        @Deprecated
        public boolean setIPv4Only(boolean v4only)
        {
            return setIPv6(!v4only);
        }

        /**
         * <p>Set the IPv6 option for the socket.</p>
         * <p>A value of true means IPv6 is enabled on the socket, while false means the socket will use only IPv4.
         * When IPv6 is enabled the socket will connect to, or accept connections from, both IPv4 and IPv6 hosts.</p>
         * <p>The default value is false, unless the following system properties are set:</p>
         * <ul>
         * <li>java.net.preferIPv4Stack=false</li>
         * <li>java.net.preferIPv6Addresses=true</li>
         * </ul>
         *
         * @param v6 A value of true will use IPv6 sockets, while the value of false will use IPv4 sockets only
         * @return true if the option was set, otherwise false
         * @see #isIPv6()
         */
        public boolean setIPv6(boolean v6)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_IPV6, v6);
        }

        /**
         * @return the keep alive setting.
         * @see #setTCPKeepAlive(int)
         */
        public int getTCPKeepAlive()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE);
        }

        /**
         * Override SO_KEEPALIVE socket option (where supported by OS) to enable keep-alive packets for a socket
         * connection. Possible values are -1, 0, 1. The default value -1 will skip all overrides and do the OS default.
         *
         * @param optVal The value of 'ZMQ_TCP_KEEPALIVE' to turn TCP keepalives on (1) or off (0).
         * @return true if the option was set, otherwise false
         */
        public boolean setTCPKeepAlive(int optVal)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE, optVal);
        }

        /**
         * @see #setDelayAttachOnConnect(boolean)
         * @deprecated use {@link #setImmediate(boolean)} instead (inverted logic: immediate = true &lt;==&gt; delay attach on connect = false)
         */
        @Deprecated
        public boolean getDelayAttachOnConnect()
        {
            return !isImmediate();
        }

        /**
         * Accept messages only when connections are made
         * <p>
         * If set to true, will delay the attachment of a pipe on connect until the underlying connection
         * has completed. This will cause the socket to block if there are no other connections, but will
         * prevent queues from filling on pipes awaiting connection
         *
         * @param value The value of 'ZMQ_DELAY_ATTACH_ON_CONNECT'. Default false.
         * @return true if the option was set
         * @deprecated use {@link #setImmediate(boolean)} instead (warning, the boolean is inverted)
         */
        @Deprecated
        public boolean setDelayAttachOnConnect(boolean value)
        {
            return setImmediate(!value);
        }

        /**
         * Retrieve the state of the attach on connect value.
         * If false, will delay the attachment of a pipe on connect until the underlying connection has completed.
         * This will cause the socket to block if there are no other connections, but will prevent queues from filling on pipes awaiting connection.
         *
         * @see #setImmediate(boolean)
         */
        public boolean isImmediate()
        {
            return (boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_IMMEDIATE);
        }

        /**
         * Retrieve the state of the attach on connect value.
         * If false, will delay the attachment of a pipe on connect until the underlying connection has completed.
         * This will cause the socket to block if there are no other connections, but will prevent queues from filling on pipes awaiting connection.
         *
         * @see #setImmediate(boolean)
         */
        public boolean getImmediate()
        {
            return isImmediate();
        }

        /**
         * Accept messages immediately or only when connections are made
         * <p>
         * By default queues will fill on outgoing connections even if the connection has not completed.
         * This can lead to "lost" messages on sockets with round-robin routing (REQ, PUSH, DEALER).
         * If this option is set to false, messages shall be queued only to completed connections.
         * This will cause the socket to block if there are no other connections,
         * but will prevent queues from filling on pipes awaiting connection.
         *
         * @param value The value of 'ZMQ_IMMEDIATE'. Default true.
         * @return true if the option was set, otherwise false.
         * @see #isImmediate()
         */
        public boolean setImmediate(boolean value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_IMMEDIATE, value);
        }

        /**
         * Sets the SOCKS5 proxy address that shall be used by the socket for the TCP connection(s).
         * Does not support SOCKS5 authentication.
         * If the endpoints are domain names instead of addresses they shall not be resolved
         * and they shall be forwarded unchanged to the SOCKS proxy service
         * in the client connection request message (address type 0x03 domain name).
         *
         * @param proxy
         * @return true if the option was set, otherwise false.
         * @see #getSocksProxy()
         */
        public boolean setSocksProxy(String proxy)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SOCKS_PROXY, proxy);
        }

        /**
         * Sets the SOCKS5 proxy address that shall be used by the socket for the TCP connection(s).
         * Does not support SOCKS5 authentication.
         * If the endpoints are domain names instead of addresses they shall not be resolved
         * and they shall be forwarded unchanged to the SOCKS proxy service
         * in the client connection request message (address type 0x03 domain name).
         *
         * @param proxy
         * @return true if the option was set, otherwise false.
         * @see #getSocksProxy()
         */
        public boolean setSocksProxy(byte[] proxy)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SOCKS_PROXY, proxy);
        }

        /**
         * The ZMQ_SOCKS_PROXY option shall retrieve the SOCKS5 proxy address in string format.
         * The returned value MAY be empty.
         *
         * @return the SOCKS5 proxy address in string format
         * @see #setSocksProxy(byte[])
         */
        public String getSocksProxy()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_SOCKS_PROXY);
        }

        /**
         * The ZMQ_LAST_ENDPOINT option shall retrieve the last endpoint bound for TCP and IPC transports.
         * The returned value will be a string in the form of a ZMQ DSN.
         * Note that if the TCP host is INADDR_ANY, indicated by a *, then the returned address will be 0.0.0.0 (for IPv4).
         */
        public String getLastEndpoint()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_LAST_ENDPOINT);
        }

        /**
         * Sets the domain for ZAP (ZMQ RFC 27) authentication.
         * For NULL security (the default on all tcp:// connections),
         * ZAP authentication only happens if you set a non-empty domain.
         * For PLAIN and CURVE security, ZAP requests are always made, if there is a ZAP handler present.
         * See http://rfc.zeromq.org/spec:27 for more details.
         *
         * @param domain the domain of ZAP authentication
         * @return true if the option was set
         * @see #getZapDomain()
         */
        public boolean setZapDomain(String domain)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ZAP_DOMAIN, domain);
        }

        /**
         * Sets the domain for ZAP (ZMQ RFC 27) authentication.
         * For NULL security (the default on all tcp:// connections),
         * ZAP authentication only happens if you set a non-empty domain.
         * For PLAIN and CURVE security, ZAP requests are always made, if there is a ZAP handler present.
         * See http://rfc.zeromq.org/spec:27 for more details.
         *
         * @param domain the domain of ZAP authentication
         * @return true if the option was set
         * @see #getZapDomain()
         */
        public boolean setZapDomain(byte[] domain)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_ZAP_DOMAIN, domain);
        }

        /**
         * The ZMQ_ZAP_DOMAIN option shall retrieve the last ZAP domain set for the socket.
         * The returned value MAY be empty.
         *
         * @return the domain of ZAP authentication
         * @see #setZapDomain(String)
         */
        public String getZapDomain()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_ZAP_DOMAIN);
        }

        /**
         * Sets the domain for ZAP (ZMQ RFC 27) authentication.
         * For NULL security (the default on all tcp:// connections),
         * ZAP authentication only happens if you set a non-empty domain.
         * For PLAIN and CURVE security, ZAP requests are always made, if there is a ZAP handler present.
         * See http://rfc.zeromq.org/spec:27 for more details.
         *
         * @param domain the domain of ZAP authentication
         * @return true if the option was set
         * @see #getZapDomain()
         */
        public boolean setZAPDomain(String domain)
        {
            return setZapDomain(domain);
        }

        /**
         * Sets the domain for ZAP (ZMQ RFC 27) authentication.
         * For NULL security (the default on all tcp:// connections),
         * ZAP authentication only happens if you set a non-empty domain.
         * For PLAIN and CURVE security, ZAP requests are always made, if there is a ZAP handler present.
         * See http://rfc.zeromq.org/spec:27 for more details.
         *
         * @param domain the domain of ZAP authentication
         * @return true if the option was set
         * @see #getZapDomain()
         */
        public boolean setZAPDomain(byte[] domain)
        {
            return setZapDomain(domain);
        }

        /**
         * The ZMQ_ZAP_DOMAIN option shall retrieve the last ZAP domain set for the socket.
         * The returned value MAY be empty.
         *
         * @return the domain of ZAP authentication
         * @see #setZapDomain(String)
         */
        public String getZAPDomain()
        {
            return getZapDomain();
        }

        /**
         * The ZMQ_SELFADDR_PROPERTY_NAME option shall retrieve the metadata record used to store the self address.
         * The returned value MAY be null or empty.
         *
         * @return the meta record name
         * @see #setSelfAddressPropertyName(String)
         */
        public String getSelfAddressPropertyName()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_SELFADDR_PROPERTY_NAME);
        }

        /**
         * Sets the field name where the self address will be stored.
         * If set to null or empty string, it will not be stored
         *
         * @param recordName the name of the field
         * @return true if the option was set
         * @see #getSelfAddressPropertyName()
         */
        public boolean setSelfAddressPropertyName(String recordName)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_SELFADDR_PROPERTY_NAME, recordName);
        }

        /**
         * Defines whether the socket will act as server for PLAIN security, see zmq_plain(7).
         * A value of true means the socket will act as PLAIN server.
         * A value of false means the socket will not act as PLAIN server,
         * and its security role then depends on other option settings.
         * Setting this to false shall reset the socket security to NULL.
         *
         * @param server true if the role of the socket should be server for PLAIN security.
         * @return true if the option was set, otherwise false.
         * @see #isAsServerPlain()
         * @deprecated the naming is inconsistent with jzmq, please use {@link #setPlainServer(boolean)} instead
         */
        @Deprecated
        public boolean setAsServerPlain(boolean server)
        {
            return setPlainServer(server);
        }

        /**
         * Defines whether the socket will act as server for PLAIN security, see zmq_plain(7).
         * A value of true means the socket will act as PLAIN server.
         * A value of false means the socket will not act as PLAIN server,
         * and its security role then depends on other option settings.
         * Setting this to false shall reset the socket security to NULL.
         *
         * @param server true if the role of the socket should be server for PLAIN security.
         * @return true if the option was set, otherwise false.
         * @see #isAsServerPlain()
         */
        public boolean setPlainServer(boolean server)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_SERVER, server);
        }

        /**
         * Returns the ZMQ_PLAIN_SERVER option, if any, previously set on the socket.
         *
         * @return true if the role of the socket should be server for the PLAIN mechanism.
         * @see #setAsServerPlain(boolean)
         * @deprecated the naming is inconsistent with jzmq, please use {@link #getPlainServer()} instead
         */
        @Deprecated
        public boolean isAsServerPlain()
        {
            return getPlainServer();
        }

        /**
         * Returns the ZMQ_PLAIN_SERVER option, if any, previously set on the socket.
         *
         * @return true if the role of the socket should be server for the PLAIN mechanism.
         * @see #setAsServerPlain(boolean)
         * @deprecated the naming is inconsistent with jzmq, please use {@link #getPlainServer()} instead
         */
        @Deprecated
        public boolean getAsServerPlain()
        {
            return getPlainServer();
        }

        /**
         * Returns the ZMQ_PLAIN_SERVER option, if any, previously set on the socket.
         *
         * @return true if the role of the socket should be server for the PLAIN mechanism.
         * @see #setAsServerPlain(boolean)
         */
        public boolean getPlainServer()
        {
            return (Boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_PLAIN_SERVER);
        }

        /**
         * Sets the username for outgoing connections over TCP or IPC.
         * If you set this to a non-null value, the security mechanism used for connections shall be PLAIN, see zmq_plain(7).
         * If you set this to a null value, the security mechanism used for connections shall be NULL, see zmq_null(3).
         *
         * @param username the username to set.
         * @return true if the option was set, otherwise false.
         */
        public boolean setPlainUsername(String username)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_USERNAME, username);
        }

        /**
         * Sets the password for outgoing connections over TCP or IPC.
         * If you set this to a non-null value, the security mechanism used for connections
         * shall be PLAIN, see zmq_plain(7).
         * If you set this to a null value, the security mechanism used for connections shall be NULL, see zmq_null(3).
         *
         * @param password the password to set.
         * @return true if the option was set, otherwise false.
         */
        public boolean setPlainPassword(String password)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_PASSWORD, password);
        }

        /**
         * Sets the username for outgoing connections over TCP or IPC.
         * If you set this to a non-null value, the security mechanism used for connections shall be PLAIN, see zmq_plain(7).
         * If you set this to a null value, the security mechanism used for connections shall be NULL, see zmq_null(3).
         *
         * @param username the username to set.
         * @return true if the option was set, otherwise false.
         */
        public boolean setPlainUsername(byte[] username)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_USERNAME, username);
        }

        /**
         * Sets the password for outgoing connections over TCP or IPC.
         * If you set this to a non-null value, the security mechanism used for connections
         * shall be PLAIN, see zmq_plain(7).
         * If you set this to a null value, the security mechanism used for connections shall be NULL, see zmq_null(3).
         *
         * @param password the password to set.
         * @return true if the option was set, otherwise false.
         */
        public boolean setPlainPassword(byte[] password)
        {
            return base.setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_PASSWORD, password);
        }

        /**
         * The ZMQ_PLAIN_USERNAME option shall retrieve the last username
         * set for the PLAIN security mechanism.
         *
         * @return the plain username.
         */
        public String getPlainUsername()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_PLAIN_USERNAME);
        }

        /**
         * The ZMQ_PLAIN_PASSWORD option shall retrieve the last password
         * set for the PLAIN security mechanism.
         * The returned value MAY be empty.
         *
         * @return the plain password.
         */
        public String getPlainPassword()
        {
            return (String) base.getSocketOptx(zmq.ZMQ.ZMQ_PLAIN_PASSWORD);
        }

        /**
         * Defines whether the socket will act as server for CURVE security, see zmq_curve(7).
         * A value of true means the socket will act as CURVE server.
         * A value of false means the socket will not act as CURVE server,
         * and its security role then depends on other option settings.
         * Setting this to false shall reset the socket security to NULL.
         * When you set this you must also set the server's secret key using the ZMQ_CURVE_SECRETKEY option.
         * A server socket does not need to know its own public key.
         *
         * @param server true if the role of the socket should be server for CURVE mechanism
         * @return true if the option was set
         * @see #isAsServerCurve()
         * @deprecated the naming is inconsistent with jzmq, please use {@link #setCurveServer(boolean)} instead
         */
        @Deprecated
        public boolean setAsServerCurve(boolean server)
        {
            return setCurveServer(server);
        }

        /**
         * Defines whether the socket will act as server for CURVE security, see zmq_curve(7).
         * A value of true means the socket will act as CURVE server.
         * A value of false means the socket will not act as CURVE server,
         * and its security role then depends on other option settings.
         * Setting this to false shall reset the socket security to NULL.
         * When you set this you must also set the server's secret key using the ZMQ_CURVE_SECRETKEY option.
         * A server socket does not need to know its own public key.
         *
         * @param server true if the role of the socket should be server for CURVE mechanism
         * @return true if the option was set
         * @see #isAsServerCurve()
         */
        public boolean setCurveServer(boolean server)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CURVE_SERVER, server);
        }

        /**
         * Tells if the socket will act as server for CURVE security.
         *
         * @return true if the role of the socket should be server for CURVE mechanism.
         * @see #setAsServerCurve(boolean)
         * @deprecated the naming is inconsistent with jzmq, please use {@link #getCurveServer()} instead
         */
        @Deprecated
        public boolean isAsServerCurve()
        {
            return getCurveServer();
        }

        /**
         * Tells if the socket will act as server for CURVE security.
         *
         * @return true if the role of the socket should be server for CURVE mechanism.
         * @see #setAsServerCurve(boolean)
         */
        public boolean getCurveServer()
        {
            return (boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_CURVE_SERVER);
        }

        /**
         * Tells if the socket will act as server for CURVE security.
         *
         * @return true if the role of the socket should be server for CURVE mechanism.
         * @see #setAsServerCurve(boolean)
         * @deprecated the naming is inconsistent with jzmq, please use {@link #getCurveServer()} instead
         */
        @Deprecated
        public boolean getAsServerCurve()
        {
            return getCurveServer();
        }

        /**
         * Sets the socket's long term public key.
         * You must set this on CURVE client sockets, see zmq_curve(7).
         * You can provide the key as 32 binary bytes, or as a 40-character string
         * encoded in the Z85 encoding format.
         * The public key must always be used with the matching secret key.
         * To generate a public/secret key pair,
         * use {@link zmq.io.mechanism.curve.Curve#keypair()} or {@link zmq.io.mechanism.curve.Curve#keypairZ85()}.
         *
         * @param key the curve public key
         * @return true if the option was set, otherwise false
         * @see #getCurvePublicKey()
         */
        public boolean setCurvePublicKey(byte[] key)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CURVE_PUBLICKEY, key);
        }

        /**
         * Sets the socket's long term server key.
         * You must set this on CURVE client sockets, see zmq_curve(7).
         * You can provide the key as 32 binary bytes, or as a 40-character string
         * encoded in the Z85 encoding format.
         * This key must have been generated together with the server's secret key.
         * To generate a public/secret key pair,
         * use {@link zmq.io.mechanism.curve.Curve#keypair()} or {@link zmq.io.mechanism.curve.Curve#keypairZ85()}.
         *
         * @param key the curve server key
         * @return true if the option was set, otherwise false
         * @see #getCurveServerKey()
         */
        public boolean setCurveServerKey(byte[] key)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CURVE_SERVERKEY, key);
        }

        /**
         * Sets the socket's long term secret key.
         * You must set this on both CURVE client and server sockets, see zmq_curve(7).
         * You can provide the key as 32 binary bytes, or as a 40-character string
         * encoded in the Z85 encoding format.
         * To generate a public/secret key pair,
         * use {@link zmq.io.mechanism.curve.Curve#keypair()} or {@link zmq.io.mechanism.curve.Curve#keypairZ85()}.
         *
         * @param key the curve secret key
         * @return true if the option was set, otherwise false
         * @see #getCurveSecretKey()
         */
        public boolean setCurveSecretKey(byte[] key)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CURVE_SECRETKEY, key);
        }

        /**
         * Retrieves the current long term public key for the socket in binary format of 32 bytes.
         *
         * @return key the curve public key
         * @see #setCurvePublicKey(byte[])
         */
        public byte[] getCurvePublicKey()
        {
            return (byte[]) base.getSocketOptx(zmq.ZMQ.ZMQ_CURVE_PUBLICKEY);
        }

        /**
         * Retrieves the current server key for the socket in binary format of 32 bytes.
         *
         * @return key the curve server key
         * @see #setCurveServerKey(byte[])
         */
        public byte[] getCurveServerKey()
        {
            return (byte[]) base.getSocketOptx(zmq.ZMQ.ZMQ_CURVE_SERVERKEY);
        }

        /**
         * Retrieves the current long term secret key for the socket in binary format of 32 bytes.
         *
         * @return key the curve secret key
         * @see #setCurveSecretKey(byte[])
         */
        public byte[] getCurveSecretKey()
        {
            return (byte[]) base.getSocketOptx(zmq.ZMQ.ZMQ_CURVE_SECRETKEY);
        }

        /**
         * The ZMQ_MECHANISM option shall retrieve the current security mechanism for the socket.
         *
         * @return the current mechanism.
         */
        public Mechanism getMechanism()
        {
            return Mechanism.find((Mechanisms) base.getSocketOptx(zmq.ZMQ.ZMQ_MECHANISM));
        }

        /**
         * When set, the socket will automatically send a hello message when a new connection is made or accepted.
         * You may set this on DEALER or ROUTER sockets.
         * The combination with ZMQ_HEARTBEAT_IVL is powerful and simplify protocols,
         * as now heartbeat and sending the hello message can be left out of protocols and be handled by zeromq.
         *
         * @param helloMsg
         * @return true if the option was set, otherwise false
         */
        public boolean setHelloMsg(byte[] helloMsg)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_HELLO_MSG, helloMsg);
        }

        /**
         * Bind to network interface. Start listening for new connections.
         *
         * @param addr the endpoint to bind to.
         * @return true if the socket was bound, otherwise false.
         */
        public boolean bind(String addr)
        {
            boolean rc = base.bind(addr);
            mayRaise();
            return rc;
        }

        /**
         * Bind to network interface to a random port. Start listening for new
         * connections.
         *
         * @param addr the endpoint to bind to.
         */
        public int bindToRandomPort(String addr)
        {
            return bindToRandomPort(addr, DYNFROM, DYNTO);
        }

        /**
         * Bind to network interface to a random port. Start listening for new
         * connections.
         *
         * @param addr the endpoint to bind to.
         * @param min  The minimum port in the range of ports to try.
         * @param max  The maximum port in the range of ports to try.
         */
        public int bindToRandomPort(String addr, int min, int max)
        {
            int port;
            for (int i = 0; i < 100; i++) { // hardcoded to 100 tries. should this be parametrised
                port = zmq.util.Utils.randomInt(max - min + 1) + min;
                if (base.bind(String.format("%s:%s", addr, port))) {
                    base.errno.set(0);
                    return port;
                }
                //                port++;
            }
            throw new ZMQException("Could not bind socket to random port.", ZError.EADDRINUSE);
        }

        /**
         * Connects the socket to an endpoint and then accepts incoming connections on that endpoint.
         * <p>
         * The endpoint is a string consisting of a transport :// followed by an address.
         * <br>
         * The transport specifies the underlying protocol to use.
         * <br>
         * The address specifies the transport-specific address to connect to.
         * <p>
         * ØMQ provides the the following transports:
         * <ul>
         * <li>tcp - unicast transport using TCP</li>
         * <li>ipc - local inter-process communication transport</li>
         * <li>inproc - local in-process (inter-thread) communication transport</li>
         * </ul>
         * Every ØMQ socket type except ZMQ_PAIR supports one-to-many and many-to-one semantics.
         * The precise semantics depend on the socket type.
         * <p>
         * For most transports and socket types the connection is not performed immediately but as needed by ØMQ.
         * <br>
         * Thus a successful call to connect(String) does not mean that the connection was or could actually be established.
         * <br>
         * Because of this, for most transports and socket types
         * the order in which a server socket is bound and a client socket is connected to it does not matter.
         * <br>
         * The first exception is when using the inproc:// transport: you must call {@link #bind(String)} before calling connect().
         * <br>
         * The second exception are ZMQ_PAIR sockets, which do not automatically reconnect to endpoints.
         * <p>
         * Following a connect(), for socket types except for ZMQ_ROUTER, the socket enters its normal ready state.
         * <br>
         * By contrast, following a {@link #bind(String)} alone, the socket enters a mute state
         * in which the socket blocks or drops messages according to the socket type.
         * <br>
         * A ZMQ_ROUTER socket enters its normal ready state for a specific peer
         * only when handshaking is complete for that peer, which may take an arbitrary time.
         *
         * @param addr the endpoint to connect to.
         * @return true if the socket was connected, otherwise false.
         */
        public boolean connect(String addr)
        {
            boolean rc = base.connect(addr);
            mayRaise();
            return rc;
        }

        /**
         * Disconnect from remote application.
         *
         * @param addr the endpoint to disconnect from.
         * @return true if successful.
         */
        public boolean disconnect(String addr)
        {
            return base.termEndpoint(addr);
        }

        /**
         * Stop accepting connections on a socket.
         *
         * @param addr the endpoint to unbind from.
         * @return true if successful.
         */
        public boolean unbind(String addr)
        {
            return base.termEndpoint(addr);
        }

        /**
         * create outgoing connection from socket and return the connection routing id in thread-safe and atomic way.
         * The function is supported only on the {@link SocketType#PEER} or {@link SocketType#RAW} socket types
         * and would return `0` with 'errno' set to 'ENOTSUP' otherwise.
         * @param addr the endpoint of the remote socket.
         * @return the endpoint routing ID.
         */
        public int connectPeer(String addr)
        {
            return base.connectPeer(addr);
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param msg the {@link Msg} to send. The message is either a single-part message by itself,
         *             or the last part of a multi-part message.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean sendMsg(Msg msg)
        {
            return sendMsg(msg, 0);
        }

        /**
         * Queues a multi-part message created from data, so it can be sent.
         *
         * @param msg the message to send. further message parts are to follow.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean sendMsgMore(Msg msg)
        {
            return sendMsg(msg, zmq.ZMQ.ZMQ_SNDMORE);
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param msg  the {@link Msg} to send. The message is either a single-part message by itself,
         *             or the last part of a multi-part message.
         * @param flags a combination (with + or |) of the flags defined below:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *              that block when there are no available peers (or all peers have full high-water mark),
         *              specifies that the operation should be performed in non-blocking mode.
         *              If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *              <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *              Specifies that the message being sent is a multi-part message,
         *              and that further message parts are to follow.</li>
         *              <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *              </ul>
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         *              This does not indicate that the message has been transmitted to the network.
         */
        public boolean sendMsg(Msg msg, int flags)
        {
            if (base.send(msg, flags)) {
                return true;
            }

            mayRaise();
            return false;
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param data the data to send. The data is either a single-part message by itself,
         *             or the last part of a multi-part message.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(String data)
        {
            return send(data.getBytes(CHARSET), 0);
        }

        /**
         * Queues a multi-part message created from data, so it can be sent.
         *
         * @param data the data to send. further message parts are to follow.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean sendMore(String data)
        {
            return send(data.getBytes(CHARSET), zmq.ZMQ.ZMQ_SNDMORE);
        }

        /**
         * Queues a message created from data.
         *
         * @param data  the data to send.
         * @param flags a combination (with + or |) of the flags defined below:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *              that block when there are no available peers (or all peers have full high-water mark),
         *              specifies that the operation should be performed in non-blocking mode.
         *              If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *              <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *              Specifies that the message being sent is a multi-part message,
         *              and that further message parts are to follow.</li>
         *              <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *              </ul>
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(String data, int flags)
        {
            return send(data.getBytes(CHARSET), flags);
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param data the data to send. The data is either a single-part message by itself,
         *             or the last part of a multi-part message.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(byte[] data)
        {
            return send(data, 0);
        }

        /**
         * Queues a multi-part message created from data, so it can be sent.
         *
         * @param data the data to send. further message parts are to follow.
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean sendMore(byte[] data)
        {
            return send(data, zmq.ZMQ.ZMQ_SNDMORE);
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param data  the data to send.
         * @param flags a combination (with + or |) of the flags defined below:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *              that block when there are no available peers (or all peers have full high-water mark),
         *              specifies that the operation should be performed in non-blocking mode.
         *              If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *              <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *              Specifies that the message being sent is a multi-part message,
         *              and that further message parts are to follow.</li>
         *              <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *              </ul>
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(byte[] data, int flags)
        {
            zmq.Msg msg = new zmq.Msg(data);
            if (base.send(msg, flags)) {
                return true;
            }

            mayRaise();
            return false;
        }

        /**
         * Queues a message created from data, so it can be sent, the call be canceled by calling cancellationToken {@link CancellationToken#cancel()}.
         * If the operation is canceled a ZMQException is thrown with error code set to {@link ZError#ECANCELED}.
         * @param data  the data to send.
         * @param flags a combination (with + or |) of the flags defined below:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *              that block when there are no available peers (or all peers have full high-water mark),
         *              specifies that the operation should be performed in non-blocking mode.
         *              If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *              <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *              Specifies that the message being sent is a multi-part message,
         *              and that further message parts are to follow.</li>
         *              <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *              </ul>
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(byte[] data, int flags, CancellationToken cancellationToken)
        {
            zmq.Msg msg = new zmq.Msg(data);
            if (base.send(msg, flags, cancellationToken.canceled)) {
                return true;
            }

            mayRaise();
            return false;
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param data   the data to send.
         * @param off    the index of the first byte to be sent.
         * @param length the number of bytes to be sent.
         * @param flags  a combination (with + or |) of the flags defined below:
         *               <ul>
         *               <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *               For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *               that block when there are no available peers (or all peers have full high-water mark),
         *               specifies that the operation should be performed in non-blocking mode.
         *               If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *               <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *               Specifies that the message being sent is a multi-part message,
         *               and that further message parts are to follow.</li>
         *               <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *               </ul>
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         */
        public boolean send(byte[] data, int off, int length, int flags)
        {
            byte[] copy = new byte[length];
            System.arraycopy(data, off, copy, 0, length);
            zmq.Msg msg = new zmq.Msg(copy);
            if (base.send(msg, flags)) {
                return true;
            }

            mayRaise();
            return false;
        }

        /**
         * Queues a message created from data, so it can be sent.
         *
         * @param data  ByteBuffer payload
         * @param flags a combination (with + or |) of the flags defined below:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              For socket types ({@link org.zeromq.ZMQ#DEALER DEALER}, {@link org.zeromq.ZMQ#PUSH PUSH})
         *              that block when there are no available peers (or all peers have full high-water mark),
         *              specifies that the operation should be performed in non-blocking mode.
         *              If the message cannot be queued on the socket, the method shall fail with errno set to EAGAIN.</li>
         *              <li>{@link org.zeromq.ZMQ#SNDMORE SNDMORE}:
         *              Specifies that the message being sent is a multi-part message,
         *              and that further message parts are to follow.</li>
         *              <li>0 : blocking send of a single-part message or the last of a multi-part message</li>
         *              </ul>
         * @return the number of bytes queued, -1 on error
         */
        public int sendByteBuffer(ByteBuffer data, int flags)
        {
            zmq.Msg msg = new zmq.Msg(data);
            if (base.send(msg, flags)) {
                return msg.size();
            }

            mayRaise();
            return -1;
        }

        /**
         * Queues a 'picture' message to the socket (or actor), so it can be sent.
         *
         * @param picture The picture is a string that defines the type of each frame.
         *                This makes it easy to send a complex multiframe message in
         *                one call. The picture can contain any of these characters,
         *                each corresponding to zero or one arguments:
         *                <table border="1">
         *                <caption><strong>Type of arguments</strong></caption>
         *                <tr><td>i = int  (stores signed integer)</td></tr>
         *                <tr><td>1 = byte (stores 8-bit unsigned integer)</td></tr>
         *                <tr><td>2 = int  (stores 16-bit unsigned integer)</td></tr>
         *                <tr><td>4 = long (stores 32-bit unsigned integer)</td></tr>
         *                <tr><td>8 = long (stores 64-bit unsigned integer)</td></tr>
         *                <tr><td>s = String</td></tr>
         *                <tr><td>b = byte[]</td></tr>
         *                <tr><td>f = ZFrame</td></tr>
         *                <tr><td>m = ZMsg (sends all frames in the ZMsg)</td></tr>
         *                <tr><td>z = sends zero-sized frame (0 arguments)</td></tr>
         *                </table>
         *                Note that s, b, f and m are encoded the same way and the choice is
         *                offered as a convenience to the sender, which may or may not already
         *                have data in a ZFrame or ZMsg. Does not change or take ownership of
         *                any arguments.
         * <p>
         *                Also see {@link #recvPicture(String)}} how to recv a
         *                multiframe picture.
         * @param args    Arguments according to the picture
         * @return true if successful, false if sending failed for any reason
         */
        @Draft
        public boolean sendPicture(String picture, Object... args)
        {
            return new ZPicture().sendPicture(this, picture, args);
        }

        /**
         * Queues a binary encoded 'picture' message to the socket (or actor), so it can be sent.
         * This method is similar to {@link #sendPicture(String, Object...)}, except the arguments
         * are encoded in a binary format that is compatible with zproto, and is designed to reduce
         * memory allocations.
         *
         * @param picture The picture argument is a string that defines the
         *                type of each argument. Supports these argument types:
         * <p>
         *                <table border="1">
         *                <caption><strong>Type of arguments</strong></caption>
         *                <tr><th style="text-align:left">pattern</th><th style="text-align:left">java type</th><th style="text-align:left">zproto type</th></tr>
         *                <tr><td>1</td><td>int</td><td>type = "number" size = "1"</td></tr>
         *                <tr><td>2</td><td>int</td><td>type = "number" size = "2"</td></tr>
         *                <tr><td>4</td><td>long</td><td>type = "number" size = "3"</td></tr>
         *                <tr><td>8</td><td>long</td><td>type = "number" size = "4"</td></tr>
         *                <tr><td>s</td><td>String, 0-255 chars</td><td>type = "string"</td></tr>
         *                <tr><td>S</td><td>String, 0-2^32-1 chars</td><td>type = "longstr"</td></tr>
         *                <tr><td>c</td><td>byte[], 0-2^32-1 bytes</td><td>type = "chunk"</td></tr>
         *                <tr><td>f</td><td>ZFrame</td><td>type = "frame"</td></tr>
         *                <tr><td>m</td><td>ZMsg</td><td>type = "msg"</td></tr>
         *                </table>
         * @param args    Arguments according to the picture
         * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
         * This does not indicate that the message has been transmitted to the network.
         * @api.note Does not change or take ownership of any arguments.
         */
        @Draft
        public boolean sendBinaryPicture(String picture, Object... args)
        {
            return new ZPicture().sendBinaryPicture(this, picture, args);
        }

        /**
         * Receives a message.
         *
         * @return the message received; null on error.
         */
        public Msg recvMsg()
        {
            return recvMsg(0);
        }

        /**
         * Receives a message.
         * <p>
         * @param flags either:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              Specifies that the operation should be performed in non-blocking mode.
         *              If there are no messages available on the specified socket,
         *              the method shall fail with errno set to EAGAIN and return null.</li>
         *              <li>0 : receive operation blocks until one message is successfully retrieved,
         *              or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *              </ul>
         * @return the message received; null on error.
         */
        public Msg recvMsg(int flags)
        {
            zmq.Msg msg = base.recv(flags);

            if (msg != null) {
                return msg;
            }

            mayRaise();
            return null;
        }

        /**
         * Receives a message.
         *
         * @return the message received, as an array of bytes; null on error.
         */
        public byte[] recv()
        {
            return recv(0);
        }

        /**
         * Receives a message.
         * <p>
         * If possible, a reference to the data is returned, without copy.
         * Otherwise a new byte array will be allocated and the data will be copied.
         * <p>
         * @param flags either:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              Specifies that the operation should be performed in non-blocking mode.
         *              If there are no messages available on the specified socket,
         *              the method shall fail with errno set to EAGAIN and return null.</li>
         *              <li>0 : receive operation blocks until one message is successfully retrieved,
         *              or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *              </ul>
         * @return the message received, as an array of bytes; null on error.
         */
        public byte[] recv(int flags)
        {
            zmq.Msg msg = base.recv(flags);

            if (msg != null) {
                return msg.data();
            }

            mayRaise();
            return null;
        }

        /**
         * Receives a message, the call be canceled by calling cancellationToken {@link CancellationToken#cancel()}.
         * If the operation is canceled a ZMQException is thrown with error code set to {@link ZError#ECANCELED}.
         * <p>
         * If possible, a reference to the data is returned, without copy.
         * Otherwise a new byte array will be allocated and the data will be copied.
         * <p>
         * @param flags either:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              Specifies that the operation should be performed in non-blocking mode.
         *              If there are no messages available on the specified socket,
         *              the method shall fail with errno set to EAGAIN and return null.</li>
         *              <li>0 : receive operation blocks until one message is successfully retrieved,
         *              or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *              </ul>
         * @param cancellationToken token to control cancellation of the receive operation.
         *                          The token can be created by calling {@link #createCancellationToken() }.
         * @return the message received, as an array of bytes; null on error.
         */
        public byte[] recv(int flags, CancellationToken cancellationToken)
        {
            zmq.Msg msg = base.recv(flags, cancellationToken.canceled);

            if (msg != null) {
                return msg.data();
            }

            mayRaise();
            return null;
        }

        /**
         * Receives a message in to a specified buffer.
         *
         * @param buffer byte[] to copy zmq message payload in to.
         * @param offset offset in buffer to write data
         * @param len    max bytes to write to buffer.
         *               If len is smaller than the incoming message size,
         *               the message will be truncated.
         * @param flags  either:
         *               <ul>
         *               <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *               Specifies that the operation should be performed in non-blocking mode.
         *               If there are no messages available on the specified socket,
         *               the method shall fail with errno set to EAGAIN and return null.</li>
         *               <li>0 : receive operation blocks until one message is successfully retrieved,
         *               or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *               </ul>
         * @return the number of bytes read, -1 on error
         */
        public int recv(byte[] buffer, int offset, int len, int flags)
        {
            zmq.Msg msg = base.recv(flags);

            if (msg != null) {
                return msg.getBytes(0, buffer, offset, len);
            }

            return -1;
        }

        /**
         * Receives a message into the specified ByteBuffer.
         *
         * @param buffer the buffer to copy the zmq message payload into
         * @param flags  either:
         *               <ul>
         *               <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *               Specifies that the operation should be performed in non-blocking mode.
         *               If there are no messages available on the specified socket,
         *               the method shall fail with errno set to EAGAIN and return null.</li>
         *               <li>0 : receive operation blocks until one message is successfully retrieved,
         *               or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *               </ul>
         * @return the number of bytes read, -1 on error
         */
        public int recvByteBuffer(ByteBuffer buffer, int flags)
        {
            zmq.Msg msg = base.recv(flags);

            if (msg != null) {
                buffer.put(msg.buf());
                return msg.size();
            }

            mayRaise();
            return -1;
        }

        /**
         * @return the message received, as a String object; null on no message.
         */
        public String recvStr()
        {
            return recvStr(0);
        }

        /**
         * Receives a message as a string.
         *
         * @param flags either:
         *              <ul>
         *              <li>{@link org.zeromq.ZMQ#DONTWAIT DONTWAIT}:
         *              Specifies that the operation should be performed in non-blocking mode.
         *              If there are no messages available on the specified socket,
         *              the method shall fail with errno set to EAGAIN and return null.</li>
         *              <li>0 : receive operation blocks until one message is successfully retrieved,
         *              or stops when timeout set by {@link #setReceiveTimeOut(int)} expires.</li>
         *              </ul>
         * @return the message received, as a String object; null on no message.
         */
        public String recvStr(int flags)
        {
            byte[] msg = recv(flags);

            if (msg != null) {
                return new String(msg, CHARSET);
            }

            return null;
        }

        /**
         * Receive a 'picture' message to the socket (or actor).
         *
         *
         * @param picture The picture is a string that defines the type of each frame.
         *                This makes it easy to recv a complex multiframe message in
         *                one call. The picture can contain any of these characters,
         *                each corresponding to zero or one elements in the result:
         *
         * <p>
         *                <table border="1">
         *                <caption><strong>Type of arguments</strong></caption>
         *                <tr><td>i = int (stores signed integer)</td></tr>
         *                <tr><td>1 = int (stores 8-bit unsigned integer)</td></tr>
         *                <tr><td>2 = int (stores 16-bit unsigned integer)</td></tr>
         *                <tr><td>4 = long (stores 32-bit unsigned integer)</td></tr>
         *                <tr><td>8 = long (stores 64-bit unsigned integer)</td></tr>
         *                <tr><td>s = String</td></tr>
         *                <tr><td>b = byte[]</td></tr>
         *                <tr><td>f = ZFrame (creates zframe)</td></tr>
         *                <tr><td>m = ZMsg (creates a zmsg with the remaing frames)</td></tr>
         *                <tr><td>z = null, asserts empty frame (0 arguments)</td></tr>
         *                </table>
         *
         *                Also see {@link #sendPicture(String, Object...)} how to send a
         *                multiframe picture.
         *
         * @return the picture elements as object array
         */
        @Draft
        public Object[] recvPicture(String picture)
        {
            return new ZPicture().recvPicture(this, picture);
        }

        /**
         * Receive a binary encoded 'picture' message from the socket (or actor).
         * This method is similar to {@link #recv()}, except the arguments are encoded
         * in a binary format that is compatible with zproto, and is designed to
         * reduce memory allocations.
         *
         * @param picture The picture argument is a string that defines
         *                the type of each argument. See {@link #sendBinaryPicture(String, Object...)}
         *                for the supported argument types.
         * @return the picture elements as object array
         **/
        @Draft
        public Object[] recvBinaryPicture(final String picture)
        {
            return new ZPicture().recvBinaryPicture(this, picture);
        }

        /**
         * Start a monitoring socket where events can be received.
         * <p>
         * Lets an application thread track socket events (like connects) on a ZeroMQ socket.
         * Each call to this method creates a {@link ZMQ#PAIR} socket and binds that to the specified inproc:// endpoint.
         * To collect the socket events, you must create your own PAIR socket, and connect that to the endpoint.
         * <br>
         * Supports only connection-oriented transports, that is, TCP, IPC.
         *
         * @param addr   the endpoint to receive events from. (must be inproc transport)
         * @param events the events of interest. A bitmask of the socket events you wish to monitor. To monitor all events, use the event value {@link ZMQ#EVENT_ALL}.
         * @return true if monitor socket setup is successful
         * @throws ZMQException
         */
        public boolean monitor(String addr, int events)
        {
            return base.monitor(addr, events);
        }

        /**
         * Register a custom event consumer.
         *
         * @param consumer  The event consumer.
         * @param events the events of interest. A bitmask of the socket events you wish to monitor. To monitor all events, use the event value {@link ZMQ#EVENT_ALL}.
         * @return true if consumer setup is successful
         * @throws ZMQException
         */
        public boolean setEventHook(ZEvent.ZEventConsummer consumer, int events)
        {
            return base.setEventHook(consumer, events);
        }

        protected void mayRaise()
        {
            int errno = base.errno();
            if (errno != 0 && errno != zmq.ZError.EAGAIN) {
                throw new ZMQException(errno);
            }
        }

        public int errno()
        {
            return base.errno();
        }

        @Override
        public String toString()
        {
            return base.toString();
        }

        public enum Mechanism
        {
            NULL(Mechanisms.NULL),
            PLAIN(Mechanisms.PLAIN),
            CURVE(Mechanisms.CURVE);
            // TODO add GSSAPI once it is implemented

            private final Mechanisms mech;

            Mechanism(Mechanisms zmq)
            {
                this.mech = zmq;
            }

            private static Mechanism find(Mechanisms mech)
            {
                for (Mechanism candidate : values()) {
                    if (candidate.mech == mech) {
                        return candidate;
                    }
                }
                return null;
            }
        }

        /**
         * Create a {@link CancellationToken} to cancel send/receive operations for this socket.
         * @return a new cancellation token associated with this socket.
         */
        public CancellationToken createCancellationToken()
        {
            return new CancellationToken(base);
        }
    }

    /**
     * Provides a mechanism for applications to multiplex input/output events in a level-triggered fashion over a set of sockets
     */
    public static class Poller implements Closeable
    {
        /**
         * For ØMQ sockets, at least one message may be received from the socket without blocking.
         * <br>
         * For standard sockets this is equivalent to the POLLIN flag of the poll() system call
         * and generally means that at least one byte of data may be read from fd without blocking.
         */
        public static final int POLLIN  = zmq.ZMQ.ZMQ_POLLIN;
        /**
         * For ØMQ sockets, at least one message may be sent to the socket without blocking.
         * <br>
         * For standard sockets this is equivalent to the POLLOUT flag of the poll() system call
         * and generally means that at least one byte of data may be written to fd without blocking.
         */
        public static final int POLLOUT = zmq.ZMQ.ZMQ_POLLOUT;
        /**
         * For standard sockets, this flag is passed through {@link zmq.ZMQ#poll(Selector, zmq.poll.PollItem[], long)} to the underlying poll() system call
         * and generally means that some sort of error condition is present on the socket specified by fd.
         * <br>
         * For ØMQ sockets this flag has no effect if set in events, and shall never be returned in revents by {@link zmq.ZMQ#poll(Selector, zmq.poll.PollItem[], long)}.
         */
        public static final int POLLERR = zmq.ZMQ.ZMQ_POLLERR;

        private static final int SIZE_DEFAULT   = 32;
        private static final int SIZE_INCREMENT = 16;

        private final Selector selector;
        private final Context  context;

        private final List<PollItem> items;

        private long timeout;

        /**
         * Class constructor.
         *
         * @param context a 0MQ context previously created.
         * @param size    the number of Sockets this poller will contain.
         */
        protected Poller(Context context, int size)
        {
            assert (context != null);
            this.context = context;

            selector = context.selector();
            assert (selector != null);

            items = new ArrayList<>(size);
            timeout = -1L;
        }

        /**
         * Class constructor.
         *
         * @param context a 0MQ context previously created.
         */
        protected Poller(Context context)
        {
            this(context, SIZE_DEFAULT);
        }

        @Override
        public void close()
        {
            context.close(selector);
        }

        /**
         * Register a Socket for polling on all events.
         *
         * @param socket the Socket we are registering.
         * @return the index identifying this Socket in the poll set.
         */
        public int register(Socket socket)
        {
            return register(socket, POLLIN | POLLOUT | POLLERR);
        }

        /**
         * Register a Channel for polling on all events.
         *
         * @param channel the Channel we are registering.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(SelectableChannel channel)
        {
            return register(channel, POLLIN | POLLOUT | POLLERR);
        }

        /**
         * Register a Socket for polling on the specified events.
         * <p>
         * Automatically grow the internal representation if needed.
         *
         * @param socket the Socket we are registering.
         * @param events a mask composed by XORing POLLIN, POLLOUT and POLLERR.
         * @return the index identifying this Socket in the poll set.
         */
        public int register(Socket socket, int events)
        {
            return registerInternal(new PollItem(socket, events));
        }

        /**
         * Register a Socket for polling on the specified events.
         * <p>
         * Automatically grow the internal representation if needed.
         *
         * @param channel the Channel we are registering.
         * @param events  a mask composed by XORing POLLIN, POLLOUT and POLLERR.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(SelectableChannel channel, int events)
        {
            return registerInternal(new PollItem(channel, events));
        }

        /**
         * Register a Channel for polling on the specified events.
         * <p>
         * Automatically grow the internal representation if needed.
         *
         * @param item the PollItem we are registering.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(PollItem item)
        {
            return registerInternal(item);
        }

        /**
         * Register a Socket for polling on the specified events.
         * <p>
         * Automatically grow the internal representation if needed.
         *
         * @param item the PollItem we are registering.
         * @return the index identifying this Socket in the poll set.
         */
        private int registerInternal(PollItem item)
        {
          items.add(item);
          return items.size() - 1;
        }

        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param socket the Socket to be unregistered
         */
        public void unregister(Socket socket)
        {
            unregisterInternal(socket);
        }

        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param channel the Socket to be unregistered
         */
        public void unregister(SelectableChannel channel)
        {
            unregisterInternal(channel);
        }

        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param socket the Socket to be unregistered
         */
        private void unregisterInternal(Object socket)
        {
            items.removeIf(item -> item.socket == socket || item.getRawSocket() == socket);
        }

        /**
         * Get the PollItem associated with an index.
         *
         * @param index the desired index.
         * @return the PollItem associated with that index (or null).
         */
        public PollItem getItem(int index)
        {
            if (index < 0 || index >= items.size()) {
                return null;
            }
            return this.items.get(index);
        }

        /**
         * Get the socket associated with an index.
         *
         * @param index the desired index.
         * @return the Socket associated with that index (or null).
         */
        public Socket getSocket(int index)
        {
            if (index < 0 || index >= items.size()) {
                return null;
            }
            return items.get(index).socket;
        }

        /**
         * Get the current poll timeout.
         *
         * @return the current poll timeout in milliseconds.
         * @deprecated Timeout handling has been moved to the poll() methods.
         */
        @Deprecated
        public long getTimeout()
        {
            return this.timeout;
        }

        /**
         * Set the poll timeout.
         *
         * @param timeout the desired poll timeout in milliseconds.
         * @deprecated Timeout handling has been moved to the poll() methods.
         */
        @Deprecated
        public void setTimeout(long timeout)
        {
            if (timeout >= -1L) {
                this.timeout = timeout;
            }
        }

        /**
         * Get the current poll set size.
         *
         * @return the current poll set size.
         */
        public int getSize()
        {
            return items.size();
        }

        /**
         * Get the index for the next position in the poll set size.
         *
         * @deprecated use getSize instead
         * @return the index for the next position in the poll set size.
         */
        @Deprecated
        public int getNext()
        {
            return items.size();
        }

        /**
         * Issue a poll call. If the poller's internal timeout value
         * has been set, use that value as timeout; otherwise, block
         * indefinitely.
         *
         * @return how many objects where signaled by poll ().
         */
        public int poll()
        {
            long tout = -1L;
            if (this.timeout > -1L) {
                tout = this.timeout;
            }
            return poll(tout);
        }

        /**
         * Issue a poll call, using the specified timeout value.
         * <p>
         * Since ZeroMQ 3.0, the timeout parameter is in <i>milliseconds</i>,
         * but prior to this the unit was <i>microseconds</i>.
         *
         * @param tout the timeout, as per zmq_poll ();
         *             if -1, it will block indefinitely until an event
         *             happens; if 0, it will return immediately;
         *             otherwise, it will wait for at most that many
         *             milliseconds/microseconds (see above).
         * @return how many objects where signaled by poll ()
         * @see "http://api.zeromq.org/3-0:zmq-poll"
         */
        public int poll(long tout)
        {
            if (tout < -1) {
                return 0;
            }
            if (items.isEmpty()) {
                return 0;
            }
            zmq.poll.PollItem[] pollItems = new zmq.poll.PollItem[items.size()];
            for (int i = 0, j = 0; i < items.size(); i++) {
                if (items.get(i) != null) {
                    pollItems[j++] = items.get(i).base;
                }
            }

            try {
                return zmq.ZMQ.poll(selector, pollItems, items.size(), tout);
            }
            catch (ZError.IOException e) {
                if (context.isTerminated()) {
                    return 0;
                }
                else {
                    throw (e);
                }
            }
        }

        /**
         * Check whether the specified element in the poll set was signaled for input.
         *
         * @param index of element
         * @return true if the element was signaled.
         */
        public boolean pollin(int index)
        {
            if (index < 0 || index >= items.size()) {
                return false;
            }

            return items.get(index).isReadable();
        }

        /**
         * Check whether the specified element in the poll set was signaled for output.
         *
         * @param index of element
         * @return true if the element was signaled.
         */
        public boolean pollout(int index)
        {
            if (index < 0 || index >= items.size()) {
                return false;
            }

            return items.get(index).isWritable();
        }

        /**
         * Check whether the specified element in the poll set was signaled for error.
         *
         * @param index of element
         * @return true if the element was signaled.
         */
        public boolean pollerr(int index)
        {
            if (index < 0 || index >= items.size()) {
                return false;
            }

            return items.get(index).isError();
        }
    }

    public static class PollItem
    {
        private final zmq.poll.PollItem base;
        private final Socket            socket;

        public PollItem(Socket socket, int ops)
        {
            this.socket = socket;
            base = new zmq.poll.PollItem(socket.base, ops);
        }

        public PollItem(SelectableChannel channel, int ops)
        {
            base = new zmq.poll.PollItem(channel, ops);
            socket = null;
        }

        final zmq.poll.PollItem base()
        {
            return base;
        }

        public final SelectableChannel getRawSocket()
        {
            return base.getRawSocket();
        }

        public final Socket getSocket()
        {
            return socket;
        }

        public final boolean isReadable()
        {
            return base.isReadable();
        }

        public final boolean isWritable()
        {
            return base.isWritable();
        }

        public final boolean isError()
        {
            return base.isError();
        }

        public final int readyOps()
        {
            return base.readyOps();
        }

        @Override
        public int hashCode()
        {
            return base.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof PollItem)) {
                return false;
            }

            PollItem target = (PollItem) obj;
            if (socket != null && socket == target.socket) {
                return true;
            }

            return getRawSocket() != null && getRawSocket() == target.getRawSocket();
        }
    }

    /**
     * Inner class: Event.
     * Monitor socket event class
     * @deprecated Uses {@link org.zeromq.ZEvent} instead
     */
    @Deprecated
    public static class Event
    {
        private final int    event;
        // To keep backward compatibility, the old value field only store integer
        // The resolved value (Error, channel or other) is stored in resolvedValue field.
        private final Object value;
        private final Object resolvedValue;
        private final String address;

        public Event(int event, Object value, String address)
        {
            this.event = event;
            this.value = value;
            this.address = address;
            this.resolvedValue = value;
        }

        private Event(int event, Object value, Object resolvedValue, String address)
        {
            this.event = event;
            this.value = value;
            this.address = address;
            this.resolvedValue = resolvedValue;
        }

        /**
         * Receive an event from a monitor socket.
         *
         * @param socket the socket
         * @param flags  the flags to apply to the receive operation.
         * @return the received event or null if no message was received.
         * @throws ZMQException
         */
        public static Event recv(Socket socket, int flags)
        {
            zmq.ZMQ.Event e = zmq.ZMQ.Event.read(socket.base, flags);
            if (e == null) {
                return null;
            }
            Object resolvedValue;
            switch (e.event) {
            case zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_PROTOCOL:
                resolvedValue = ZMonitor.ProtocolCode.findByCode((Integer) e.arg);
                break;
            case zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED:
            case zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED:
            case zmq.ZMQ.ZMQ_EVENT_BIND_FAILED:
            case zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_FAILED_NO_DETAIL:
                resolvedValue = Error.findByCode((Integer) e.arg);
                break;
            case zmq.ZMQ.ZMQ_EVENT_CONNECTED:
            case zmq.ZMQ.ZMQ_EVENT_LISTENING:
            case zmq.ZMQ.ZMQ_EVENT_ACCEPTED:
            case zmq.ZMQ.ZMQ_EVENT_CLOSED:
            case zmq.ZMQ.ZMQ_EVENT_DISCONNECTED:
                resolvedValue = e.getChannel(socket.base);
                break;
            case zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED:
            case zmq.ZMQ.ZMQ_EVENT_HANDSHAKE_SUCCEEDED:
            case zmq.ZMQ.ZMQ_EVENT_MONITOR_STOPPED:
                resolvedValue = null;
                break;
            default:
                resolvedValue = e.arg;
            }
            return new Event(e.event, e.arg, resolvedValue, e.addr);
        }

        /**
         * Receive an event from a monitor socket.
         * Does a blocking recv.
         *
         * @param socket the socket
         * @return the received event.
         * @throws ZMQException
         */
        public static Event recv(Socket socket)
        {
            return Event.recv(socket, 0);
        }

        public int getEvent()
        {
            return event;
        }

        public Object getValue()
        {
            return value;
        }

        public String getAddress()
        {
            return address;
        }

        /**
         * Used to check if the event is an error.
         * <p>
         * Generally, any event that define the errno is
         * considered as an error.
         * @return true if the evant was an error
         */
        public boolean isError()
        {
            switch (event) {
            case EVENT_CLOSE_FAILED:
            case EVENT_ACCEPT_FAILED:
            case EVENT_BIND_FAILED:
            case HANDSHAKE_FAILED_PROTOCOL:
            case HANDSHAKE_FAILED_NO_DETAIL:
                return true;
            default:
                return false;
            }
        }

        /**
         * Used to check if the event is a warning.
         * <p>
         * Generally, any event that return an authentication failure is
         * considered as a warning.
         * @return
         */
        public boolean isWarn()
        {
            switch (event) {
            case HANDSHAKE_FAILED_AUTH:
                return true;
            default:
                return false;
            }
        }

        /**
         * Return the argument as an integer or a Enum of the appropriate type if available.
         * <p>
         * It returns objects of type:
         * <ul>
         * <li> {@link org.zeromq.ZMonitor.ProtocolCode} for a handshake protocol error.</li>
         * <li> {@link org.zeromq.ZMQ.Error} for any other error.</li>
         * <li> {@link java.lang.Integer} when available.</li>
         * <li> null when no relevant value available.</li>
         * </ul>
         * @param <M> The expected type of the returned object
         * @return The resolved value.
         */
        @SuppressWarnings("unchecked")
        public <M> M resolveValue()
        {
            return (M) resolvedValue;
        }
    }

    /**
     * Class that interfaces the generation of CURVE key pairs.
     *
     * <p>The CURVE mechanism defines a mechanism for secure authentication and confidentiality for communications between a client and a server.
     * CURVE is intended for use on public networks.
     * The CURVE mechanism is defined by this document: http://rfc.zeromq.org/spec:25.</p>
     *
     * <h2>Client and server roles</h2>
     *
     * <p>A socket using CURVE can be either client or server, at any moment, but not both. The role is independent of bind/connect direction.
     * A socket can change roles at any point by setting new options. The role affects all connect and bind calls that follow it.</p>
     *
     * <p>To become a CURVE server, the application sets the {@link ZMQ.Socket#setAsServerCurve(boolean)} option on the socket,
     * and then sets the {@link ZMQ.Socket#setCurveSecretKey(byte[])} option to provide the socket with its long-term secret key.
     * The application does not provide the socket with its long-term public key, which is used only by clients.</p>
     *
     * <p>To become a CURVE client, the application sets the {@link ZMQ.Socket#setCurveServerKey(byte[])} option
     * with the long-term public key of the server it intends to connect to, or accept connections from, next.
     * The application then sets the {@link ZMQ.Socket#setCurvePublicKey(byte[])} and {@link ZMQ.Socket#setCurveSecretKey(byte[])} options with its client long-term key pair.
     * If the server does authentication it will be based on the client's long term public key.</p>
     *
     * <h3>Key encoding</h3>
     *
     * <p>The standard representation for keys in source code is either 32 bytes of base 256 (binary) data,
     * or 40 characters of base 85 data encoded using the Z85 algorithm defined by http://rfc.zeromq.org/spec:32.
     * The Z85 algorithm is designed to produce printable key strings for use in configuration files, the command line, and code.
     * There is a reference implementation in C at https://github.com/zeromq/rfc/tree/master/src.</p>
     *
     * <h3>Test key values</h3>
     *
     * <p>For test cases, the client shall use this long-term key pair (specified as hexadecimal and in Z85):</p>
     * <ul>
     * <li>public:
     * <p>BB88471D65E2659B30C55A5321CEBB5AAB2B70A398645C26DCA2B2FCB43FC518</p>
     * <p>{@code Yne@$w-vo<fVvi]a<NY6T1ed:M$fCG*[IaLV{hID}</p>
     * </li>
     * <li>secret:
     * <p>7BB864B489AFA3671FBE69101F94B38972F24816DFB01B51656B3FEC8DFD0888</p>
     * <p>{@code D:)Q[IlAW!ahhC2ac:9*A}h:p?([4%wOTJ%JR%cs}</p>
     * </li>
     * </ul>
     *
     * <p>And the server shall use this long-term key pair (specified as hexadecimal and in Z85):</p>
     * <ul>
     * <li>public:
     * <p>54FCBA24E93249969316FB617C872BB0C1D1FF14800427C594CBFACF1BC2D652</p>
     * <p>{@code rq:rM>}U?@Lns47E1%kR.o@n%FcmmsL/@{H8]yf7}</p>
     * </li>
     * <li>secret:
     * <p>8E0BDD697628B91D8F245587EE95C5B04D48963F79259877B49CD9063AEAD3B7</p>
     * <p>{@code JTKVSB%%)wK0E.X)V>+}o?pNmC{O&amp;4W4b!Ni{Lh6}</p>
     * </li>
     * </ul>
     */
    public static class Curve
    {
        public static final int KEY_SIZE = Options.CURVE_KEYSIZE;
        public static final int KEY_SIZE_Z85 = Options.CURVE_KEYSIZE_Z85;

        /**
         * <p>Returns a newly generated random keypair consisting of a public key
         * and a secret key.</p>
         *
         * <p>The keys are encoded using {@link #z85Encode}.</p>
         *
         * @return Randomly generated {@link KeyPair}
         */
        public static KeyPair generateKeyPair()
        {
            String[] keys = new zmq.io.mechanism.curve.Curve().keypairZ85();
            return new KeyPair(keys[0], keys[1]);
        }

        /**
         * <p>The function shall decode given key encoded as Z85 string into byte array.</p>
         * <p>The length of string shall be divisible by 5.</p>
         * <p>The decoding shall follow the ZMQ RFC 32 specification.</p>
         *
         * @param key Key to be decoded
         * @return The resulting key as byte array
         */
        public static byte[] z85Decode(String key)
        {
            return Z85.decode(key);
        }

        /**
         * <p>Encodes the binary block specified by data into a string.</p>
         * <p>The size of the binary block must be divisible by 4.</p>
         * <p>A 32-byte CURVE key is encoded as 40 ASCII characters plus a null terminator.</p>
         * <p>The function shall encode the binary block specified into a string.</p>
         * <p>The encoding shall follow the ZMQ RFC 32 specification.</p>
         *
         * @param key Key to be encoded
         * @return The resulting key as String in Z85
         */
        public static String z85Encode(byte[] key)
        {
            return zmq.io.mechanism.curve.Curve.z85EncodePublic(key);
        }

        /**
         * A container for a public and a corresponding secret key.
         * Keys have to be encoded in Z85 format.
         */
        public static class KeyPair
        {
            /**
             * Z85-encoded public key.
             */
            public final String publicKey;

            /**
             * Z85-encoded secret key.
             */
            public final String secretKey;

            public KeyPair(final String publicKey, final String secretKey)
            {
                Utils.checkArgument(publicKey != null, "Public key cannot be null");
                Utils.checkArgument(publicKey.length() == Curve.KEY_SIZE_Z85, "Public key has to be Z85 format");
                Utils.checkArgument(secretKey == null || secretKey.length() == Curve.KEY_SIZE_Z85, "Secret key has to be null or in Z85 format");
                this.publicKey = publicKey;
                this.secretKey = secretKey;
            }
        }
    }

    /**
     * A cancellation token that allows canceling ongoing Socket send/receive operations.
     * When calling send/receive you can provide the cancellation token as an additional parameter.
     * To create a cancellation token call {@link Socket#createCancellationToken()}.
     *
     */
    public static class CancellationToken
    {
        protected final AtomicBoolean canceled;
        final SocketBase socket;

        protected CancellationToken(SocketBase socket)
        {
            this.socket = socket;
            canceled = new AtomicBoolean(false);
        }

        public boolean isCancellationRequested()
        {
            return canceled.get();
        }

        /**
         * Reset the cancellation token in order to reuse the token with another send/receive call.
         */
        public void reset()
        {
            canceled.set(false);
        }

        /**
         * Cancel a pending the send/receive operation.
         */
        public void cancel()
        {
            socket.cancel(canceled);
        }
    }
}
