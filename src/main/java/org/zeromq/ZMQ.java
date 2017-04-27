package org.zeromq;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZError.CtxTerminatedException;
import zmq.io.coder.IDecoder;
import zmq.io.coder.IEncoder;
import zmq.io.mechanism.Mechanisms;

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

    // Socket types, used when creating a Socket.
    /**
     * Flag to specify a exclusive pair of sockets.
     */
    public static final int PAIR       = zmq.ZMQ.ZMQ_PAIR;
    /**
     * Flag to specify a PUB socket, receiving side must be a SUB or XSUB.
     */
    public static final int PUB        = zmq.ZMQ.ZMQ_PUB;
    /**
     * Flag to specify the receiving part of the PUB or XPUB socket.
     */
    public static final int SUB        = zmq.ZMQ.ZMQ_SUB;
    /**
     * Flag to specify a REQ socket, receiving side must be a REP.
     */
    public static final int REQ        = zmq.ZMQ.ZMQ_REQ;
    /**
     * Flag to specify the receiving part of a REQ socket.
     */
    public static final int REP        = zmq.ZMQ.ZMQ_REP;
    /**
     * Flag to specify a DEALER socket (aka XREQ).
     * DEALER is really a combined ventilator / sink
     * that does load-balancing on output and fair-queuing on input
     * with no other semantics. It is the only socket type that lets
     * you shuffle messages out to N nodes and shuffle the replies
     * back, in a raw bidirectional asynch pattern.
     */
    public static final int DEALER     = zmq.ZMQ.ZMQ_DEALER;
    /**
    * Old alias for DEALER flag.
    * Flag to specify a XREQ socket, receiving side must be a XREP.
    *
    * @deprecated  As of release 3.0 of zeromq, replaced by {@link #DEALER}
    */
    @Deprecated
    public static final int XREQ       = DEALER;
    /**
     * Flag to specify ROUTER socket (aka XREP).
     * ROUTER is the socket that creates and consumes request-reply
     * routing envelopes. It is the only socket type that lets you route
     * messages to specific connections if you know their identities.
     */
    public static final int ROUTER     = zmq.ZMQ.ZMQ_ROUTER;
    /**
     * Old alias for ROUTER flag.
     * Flag to specify the receiving part of a XREQ socket.
     *
     * @deprecated  As of release 3.0 of zeromq, replaced by {@link #ROUTER}
     */
    @Deprecated
    public static final int XREP       = ROUTER;
    /**
     * Flag to specify the receiving part of a PUSH socket.
     */
    public static final int PULL       = zmq.ZMQ.ZMQ_PULL;
    /**
     * Flag to specify a PUSH socket, receiving side must be a PULL.
     */
    public static final int PUSH       = zmq.ZMQ.ZMQ_PUSH;
    /**
     * Flag to specify a XPUB socket, receiving side must be a SUB or XSUB.
     * Subscriptions can be received as a message. Subscriptions start with
     * a '1' byte. Unsubscriptions start with a '0' byte.
     */
    public static final int XPUB       = zmq.ZMQ.ZMQ_XPUB;
    /**
     * Flag to specify the receiving part of the PUB or XPUB socket. Allows
     */
    public static final int XSUB       = zmq.ZMQ.ZMQ_XSUB;
    /**
     * Flag to specify a STREAM socket.
     */
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
    public static final int EVENT_CONNECTED       = zmq.ZMQ.ZMQ_EVENT_CONNECTED;
    /**
     * EVENT_CONNECT_DELAYED: synchronous connect failed, it's being polled.
     * The EVENT_CONNECT_DELAYED event triggers when an immediate connection
     * attempt is delayed and its completion is being polled for. Value has
     * no meaning.
     */
    public static final int EVENT_CONNECT_DELAYED = zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED;
    /**
     * @see org.zeromq.ZMQ#EVENT_CONNECT_DELAYED
     */
    @Deprecated
    public static final int EVENT_DELAYED         = EVENT_CONNECT_DELAYED;
    /**
     * EVENT_CONNECT_RETRIED: asynchronous connect / reconnection attempt.
     * The EVENT_CONNECT_RETRIED event triggers when a connection attempt is
     * being handled by reconnect timer. The reconnect interval's recomputed
     * for each attempt. Value is the reconnect interval.
     */
    public static final int EVENT_CONNECT_RETRIED = zmq.ZMQ.ZMQ_EVENT_CONNECT_RETRIED;
    /**
     * @see org.zeromq.ZMQ#EVENT_CONNECT_RETRIED
     */
    @Deprecated
    public static final int EVENT_RETRIED         = EVENT_CONNECT_RETRIED;
    /**
     * EVENT_LISTENING: socket bound to an address, ready to accept connections.
     * The EVENT_LISTENING event triggers when a socket's successfully bound to
     * a an interface. Value is the FD of the newly bound socket.
     */
    public static final int EVENT_LISTENING       = zmq.ZMQ.ZMQ_EVENT_LISTENING;
    /**
     * EVENT_BIND_FAILED: socket could not bind to an address.
     * The EVENT_BIND_FAILED event triggers when a socket could not bind to a
     * given interface. Value is the errno generated by the bind call.
     */
    public static final int EVENT_BIND_FAILED     = zmq.ZMQ.ZMQ_EVENT_BIND_FAILED;
    /**
     * EVENT_ACCEPTED: connection accepted to bound interface.
     * The EVENT_ACCEPTED event triggers when a connection from a remote peer
     * has been established with a socket's listen address. Value is the FD of
     * the accepted socket.
     */
    public static final int EVENT_ACCEPTED        = zmq.ZMQ.ZMQ_EVENT_ACCEPTED;
    /**
     * EVENT_ACCEPT_FAILED: could not accept client connection.
     * The EVENT_ACCEPT_FAILED event triggers when a connection attempt to a
     * socket's bound address fails. Value is the errno generated by accept.
     */
    public static final int EVENT_ACCEPT_FAILED   = zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED;
    /**
     * EVENT_CLOSED: connection closed.
     * The EVENT_CLOSED event triggers when a connection's underlying
     * descriptor has been closed. Value is the former FD of the for the
     * closed socket. FD has been closed already!
     */
    public static final int EVENT_CLOSED          = zmq.ZMQ.ZMQ_EVENT_CLOSED;
    /**
     * EVENT_CLOSE_FAILED: connection couldn't be closed.
     * The EVENT_CLOSE_FAILED event triggers when a descriptor could not be
     * released back to the OS. Implementation note: ONLY FOR IPC SOCKETS.
     * Value is the errno generated by unlink.
     */
    public static final int EVENT_CLOSE_FAILED    = zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED;
    /**
     * EVENT_DISCONNECTED: broken session.
     * The EVENT_DISCONNECTED event triggers when the stream engine (tcp and
     * ipc specific) detects a corrupted / broken session. Value is the FD of
     * the socket.
     */
    public static final int EVENT_DISCONNECTED    = zmq.ZMQ.ZMQ_EVENT_DISCONNECTED;
    /**
     * EVENT_MONITOR_STOPPED: monitor has been stopped.
     * The EVENT_MONITOR_STOPPED event triggers when the monitor for a socket is
     * stopped.
     */
    public static final int EVENT_MONITOR_STOPPED = zmq.ZMQ.ZMQ_EVENT_MONITOR_STOPPED;
    /**
     * EVENT_ALL: all events known.
     * The EVENT_ALL constant can be used to set up a monitor for all known events.
     */
    public static final int EVENT_ALL             = zmq.ZMQ.ZMQ_EVENT_ALL;

    public static final byte[] MESSAGE_SEPARATOR = new byte[0];

    public static final byte[] SUBSCRIPTION_ALL = new byte[0];

    public static final Charset CHARSET = zmq.ZMQ.CHARSET;

    private ZMQ()
    {
    }

    /**
     * Create a new Context.
     *
     * @param ioThreads
     *            Number of threads to use, usually 1 is sufficient for most use cases.
     * @return the Context
     */
    public static Context context(int ioThreads)
    {
        return new Context(ioThreads);
    }

    public static class Context implements Closeable
    {
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Ctx           ctx;

        /**
         * Class constructor.
         *
         * @param ioThreads
         *            size of the threads pool to handle I/O operations.
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
            return !ctx.checkTag();
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
         * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ
         * Context has been disposed of.
         */
        public void term()
        {
            if (closed.compareAndSet(false, true)) {
                ctx.terminate();
            }
        }

        /**
         * Create a new Socket within this context.
         *
         * @param type
         *            the socket type.
         * @return the newly created Socket.
         */
        public Socket socket(int type)
        {
            return new Socket(this, type);
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
         *
         * @return the newly created Poller.
         */
        public Poller poller()
        {
            return new Poller(this);
        }

        /**
         * Create a new Poller within this context, with a specified initial size.
         *
         * @param size
         *            the poller initial size.
         * @return the newly created Poller.
         */
        public Poller poller(int size)
        {
            return new Poller(this, size);
        }

        @Override
        public void close()
        {
            term();
        }
    }

    public static final class Socket implements Closeable
    {
        //  This port range is defined by IANA for dynamic or private ports
        //  We use this when choosing a port for dynamic binding.
        private static final int DYNFROM = 0xc000;
        private static final int DYNTO   = 0xffff;

        private final Ctx           ctx;
        private final SocketBase    base;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);

        /**
         * Class constructor.
         *
         * @param context
         *            a 0MQ context previously created.
         * @param type
         *            the socket type.
         */
        protected Socket(Context context, int type)
        {
            ctx = context.ctx;
            base = ctx.createSocket(type);
        }

        protected Socket(SocketBase base)
        {
            ctx = null;
            this.base = base;
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
         * has been disposed of.
         */
        @Override
        public void close()
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
         * The following outlines the different behaviours: A value of -1 specifies an infinite linger period.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until all pending messages have been sent to a peer.
         * The value of 0 specifies no linger period. Pending messages shall be discarded immediately after a call to disconnect() or close().
         * Positive values specify an upper bound for the linger period in milliseconds.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until either all pending messages have been sent to a peer,
         * or the linger period expires, after which any pending messages shall be discarded.
         *
         * @param value
         *            the linger period in milliseconds.
         * @return true if the option was set, otherwise false
         * @deprecated the linger option has only integer range, use {@link #setLinger(int)} instead
         * @see #getLinger()
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
         * The following outlines the different behaviours: A value of -1 specifies an infinite linger period.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until all pending messages have been sent to a peer.
         * The value of 0 specifies no linger period. Pending messages shall be discarded immediately after a call to disconnect() or close().
         * Positive values specify an upper bound for the linger period in milliseconds.
         * Pending messages shall not be discarded after a call to disconnect() or close();
         * attempting to terminate the socket's context with Ctx#term() shall block until either all pending messages have been sent to a peer,
         * or the linger period expires, after which any pending messages shall be discarded.
         *
         * @param value
         *            the linger period in milliseconds.
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
         *
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
         * @deprecated reconnect interval option uses integer range, use {@link #setReconnectIVL(int)} instead
         * @see #getReconnectIVL()
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
         * @deprecated this option uses integer range, use {@link #setBacklog(int)} instead.
         * @see #getBacklog()
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
         * @deprecated this option uses integer range, use {@link #setReconnectIVLMax(int)} instead
         * @see #getReconnectIVLMax()
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
         *
         * CAUTION: ØMQ does not guarantee that the socket will accept as many as ZMQ_SNDHWM messages,
         * and the actual limit may be as much as 60-70% lower depending on the flow of messages on the socket.
         *
         * @return true if the option was set, otherwise false.
         * @deprecated this option uses integer range, use {@link #setSndHWM(int)} instead
         * @see #getSndHWM()
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
         *
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
         * @deprecated this option uses integer range, use {@link #setRcvHWM(int)} instead
         * @see #getRcvHWM()
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
         * @see #setHWM(int)
         *
         * @return the High Water Mark.
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
         *
         * If this limit has been reached the socket shall enter an exceptional state and depending
         * on the socket type, 0MQ shall take appropriate action such as blocking or dropping sent
         * messages. Refer to the individual socket descriptions in the man page of zmq_socket[3] for
         * details on the exact action taken for each socket type.
         *
         * @param hwm
         *            the number of messages to queue.
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
         *
         * If this limit has been reached the socket shall enter an exceptional state and depending
         * on the socket type, 0MQ shall take appropriate action such as blocking or dropping sent
         * messages. Refer to the individual socket descriptions in the man page of zmq_socket[3] for
         * details on the exact action taken for each socket type.
         *
         * @param hwm
         *            the number of messages to queue.
         * @return true if the option was set, otherwise false
         */
        public boolean setHWM(int hwm)
        {
            boolean set = true;
            set |= setSndHWM(hwm);
            set |= setRcvHWM(hwm);
            return set;
        }

        /**
         * @see #setSwap(long)
         *
         * @return the number of messages to swap at most.
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
         * @param value
         *            The value of 'ZMQ_SWAP' defines the maximum size of the swap space in bytes.
         */
        @Deprecated
        public boolean setSwap(long value)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * @see #setAffinity(long)
         *
         * @return the affinity.
         */
        public long getAffinity()
        {
            return (Long) base.getSocketOptx(zmq.ZMQ.ZMQ_AFFINITY);
        }

        /**
         * Get the Affinity. The 'ZMQ_AFFINITY' option shall set the I/O thread affinity for newly
         * created connections on the specified 'socket'.
         *
         * Affinity determines which threads from the 0MQ I/O thread pool associated with the
         * socket's _context_ shall handle newly created connections. A value of zero specifies no
         * affinity, meaning that work shall be distributed fairly among all 0MQ I/O threads in the
         * thread pool. For non-zero values, the lowest bit corresponds to thread 1, second lowest
         * bit to thread 2 and so on. For example, a value of 3 specifies that subsequent
         * connections on 'socket' shall be handled exclusively by I/O threads 1 and 2.
         *
         * See also  in the man page of init[3] for details on allocating the number of I/O threads for a
         * specific _context_.
         *
         * @param value
         *            the io_thread affinity.
         * @return true if the option was set, otherwise false
         */
        public boolean setAffinity(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_AFFINITY, value);
        }

        /**
         * @see #setIdentity(byte[])
         *
         * @return the Identitiy.
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
         *
         * If the socket has no identity, each run of an application is completely separate from
         * other runs. However, with identity set the socket shall re-use any existing 0MQ
         * infrastructure configured by the previous run(s). Thus the application may receive
         * messages that were sent in the meantime, _message queue_ limits shall be shared with
         * previous run(s) and so on.
         *
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
         * @see #setRate(long)
         *
         * @return the Rate.
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
         *
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
        public boolean getReqCorrelate()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_REQ_CORRELATE) > 0;
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
        public boolean getReqRelaxed()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_REQ_CORRELATE) > 0;
        }

        /**
         * @see #setMulticastLoop(boolean)
         *
         * @return the Multicast Loop.
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
         * @see #setMulticastHops(long)
         *
         * @return the Multicast Hops.
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
         * @see #setTCPKeepAlive(long)
         *
         * @return the keep alive setting.
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
         * @see #setTCPKeepAliveCount(long)
         *
         * @return the keep alive count.
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
         *            dependent.
         * @return true if the option was set, otherwise false.
         */
        public boolean setTCPKeepAliveInterval(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_INTVL, Long.valueOf(value).intValue());
        }

        /**
         * @see #setTCPKeepAliveInterval(long)
         *
         * @return the keep alive interval.
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
         *            over the socket and the first keepalive probe. Unit is OS dependent.
         * @return true if the option was set, otherwise false
         */
        public boolean setTCPKeepAliveIdle(long value)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_IDLE, Long.valueOf(value).intValue());
        }

        /**
         * @see #setTCPKeepAliveIdle(long)
         *
         * @return the keep alive idle value.
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
         * @deprecated this option uses integer range, use {@link #setSendBufferSize(int)} instead
         * @see #getSendBufferSize()
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
         * @deprecated this option uses integer range, use {@link #setReceiveBufferSize(int)} instead
         * @see #getReceiveBufferSize()
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
         *
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
         *
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
         * Set custom Encoder
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
         * @return the threshold
         */
        public int getMsgAllocationHeapThreshold()
        {
            return base.getSocketOpt(zmq.ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD);
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
         *                  A value of true, the ROUTER socket shall hand-over the connection to the new client and disconnect the existing one
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

        /**
         * @see #setIPv4Only (boolean)
         *
         * @return the IPV4ONLY
         * @deprecated use {@link #isIPv6()} instead (inverted logic: ipv4 = true <==> ipv6 = false)
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
         * @deprecated use {@link #setIPv6(boolean)} instead (inverted logic: ipv4 = true <==> ipv6 = false)
         */
        @Deprecated
        public boolean setIPv4Only(boolean v4only)
        {
            return setIPv6(!v4only);
        }

        /**
         * Set the IPv6 option for the socket.
         * A value of true means IPv6 is enabled on the socket, while false means the socket will use only IPv4.
         * When IPv6 is enabled the socket will connect to, or accept connections from, both IPv4 and IPv6 hosts.
         *
         * @param v6 A value of true will use IPv6 sockets, while the value of false will use IPv4 sockets
         * @return true if the option was set, otherwise false
         * @see #isIPv6()
         */
        public boolean setIPv6(boolean v6)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_IPV6, v6);
        }

        /**
         * @see #setTCPKeepAlive(int)
         *
         * @return the keep alive setting.
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
         *
         * @deprecated use {@link #setImmediate(boolean)} instead (inverted logic: immediate = true <==> delay attach on connect = false)
         */
        @Deprecated
        public boolean getDelayAttachOnConnect()
        {
            return !isImmediate();
        }

        /**
         * Accept messages only when connections are made
         *
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
         *
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
        public boolean setAsServerPlain(boolean server)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_PLAIN_SERVER, server);
        }

        /**
         * Returns the ZMQ_PLAIN_SERVER option, if any, previously set on the socket.
         *
         * @return true if the role of the socket should be server for the PLAIN mechanism.
         * @see #setAsServerPlain(boolean)
         */
        public boolean isAsServerPlain()
        {
            return (Boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_PLAIN_SERVER);
        }

        /**
         * Returns the ZMQ_PLAIN_SERVER option, if any, previously set on the socket.
         *
         * @return true if the role of the socket should be server for the PLAIN mechanism.
         * @see #setAsServerPlain(boolean)
         */
        public boolean getAsServerPlain()
        {
            return isAsServerPlain();
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
         */
        public boolean setAsServerCurve(boolean server)
        {
            return setSocketOpt(zmq.ZMQ.ZMQ_CURVE_SERVER, server);
        }

        /**
         * Tells if the socket will act as server for CURVE security.
         *
         * @return true if the role of the socket should be server for CURVE mechanism.
         * @see #setAsServerCurve(boolean)
         */
        public boolean isAsServerCurve()
        {
            return (boolean) base.getSocketOptx(zmq.ZMQ.ZMQ_CURVE_SERVER);
        }

        /**
         * Tells if the socket will act as server for CURVE security.
         *
         * @return true if the role of the socket should be server for CURVE mechanism.
         * @see #setAsServerCurve(boolean)
         */
        public boolean getAsServerCurve()
        {
            return isAsServerCurve();
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

        public static enum Mechanism
        {
            NULL(Mechanisms.NULL),
            PLAIN(Mechanisms.PLAIN),
            CURVE(Mechanisms.CURVE);

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
         * The ZMQ_MECHANISM option shall retrieve the current security mechanism for the socket.
         *
         * @return the current mechanism.
         */
        public Mechanism getMechanism()
        {
            return Mechanism.find((Mechanisms) base.getSocketOptx(zmq.ZMQ.ZMQ_MECHANISM));
        }

        /**
         * Bind to network interface. Start listening for new connections.
         *
         * @param addr
         *            the endpoint to bind to.
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
         * @param addr
         *            the endpoint to bind to.
         */
        public int bindToRandomPort(String addr)
        {
            return bindToRandomPort(addr, DYNFROM, DYNTO);
        }

        /**
         * Bind to network interface to a random port. Start listening for new
         * connections.
         *
         * @param addr
         *            the endpoint to bind to.
         * @param min
         *            The minimum port in the range of ports to try.
         * @param max
         *            The maximum port in the range of ports to try.
         */
        public int bindToRandomPort(String addr, int min, int max)
        {
            int port;
            Random rand = new Random();
            //            int port = min;
            //            while (port <= max) {
            for (int i = 0; i < 100; i++) { // hardcoded to 100 tries. should this be parametrised
                port = rand.nextInt(max - min + 1) + min;
                if (base.bind(String.format("%s:%s", addr, port))) {
                    return port;
                }
                //                port++;
            }
            throw new ZMQException("Could not bind socket to random port.", ZError.EADDRINUSE);
        }

        /**
         * Connect to remote application.
         *
         * @param addr
         *            the endpoint to connect to.
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
         * @param addr
         *            the endpoint to disconnect from.
         * @return true if successful.
         */
        public boolean disconnect(String addr)
        {
            return base.termEndpoint(addr);
        }

        /**
         * Stop accepting connections on a socket.
         *
         * @param addr
         *            the endpoint to unbind from.
         * @return true if successful.
         */
        public boolean unbind(String addr)
        {
            return base.termEndpoint(addr);
        }

        public boolean send(String data)
        {
            return send(data.getBytes(CHARSET), 0);
        }

        public boolean sendMore(String data)
        {
            return send(data.getBytes(CHARSET), zmq.ZMQ.ZMQ_SNDMORE);
        }

        public boolean send(String data, int flags)
        {
            return send(data.getBytes(CHARSET), flags);
        }

        public boolean send(byte[] data)
        {
            return send(data, 0);
        }

        public boolean sendMore(byte[] data)
        {
            return send(data, zmq.ZMQ.ZMQ_SNDMORE);
        }

        public boolean send(byte[] data, int flags)
        {
            zmq.Msg msg = new zmq.Msg(data);
            if (base.send(msg, flags)) {
                return true;
            }

            mayRaise();
            return false;
        }

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
         * Send a message
         *
         * @param data ByteBuffer payload
         * @param flags the flags to apply to the send operation
         * @return the number of bytes sent, -1 on error
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
         * Receive a message.
         *
         * @return the message received, as an array of bytes; null on error.
         */
        public byte[] recv()
        {
            return recv(0);
        }

        /**
         * Receive a message.
         *
         * @param flags
         *            the flags to apply to the receive operation.
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
         * Receive a message in to a specified buffer.
         *
         * @param buffer
         *            byte[] to copy zmq message payload in to.
         * @param offset
         *            offset in buffer to write data
         * @param len
         *            max bytes to write to buffer.
         *            If len is smaller than the incoming message size,
         *            the message will be truncated.
         * @param flags
         *            the flags to apply to the receive operation.
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
         * Receive a message into the specified ByteBuffer
         *
         * @param buffer the buffer to copy the zmq message payload into
         * @param flags the flags to apply to the receive operation
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
         *
         * @return the message received, as a String object; null on no message.
         */
        public String recvStr()
        {
            return recvStr(0);
        }

        /**
         *
         * @param flags the flags to apply to the receive operation.
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
         * Start a monitoring socket where events can be received.
         *
         * @param addr the endpoint to receive events from. (must be inproc transport)
         * @param events the events of interest.
         * @return true if monitor socket setup is successful
         * @throws ZMQException
         */
        public boolean monitor(String addr, int events)
        {
            return base.monitor(addr, events);
        }

        private void mayRaise()
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
    }

    public static class Poller
    {
        /**
         * These values can be ORed to specify what we want to poll for.
         */
        public static final int POLLIN  = zmq.ZMQ.ZMQ_POLLIN;
        public static final int POLLOUT = zmq.ZMQ.ZMQ_POLLOUT;
        public static final int POLLERR = zmq.ZMQ.ZMQ_POLLERR;

        private static final int SIZE_DEFAULT   = 32;
        private static final int SIZE_INCREMENT = 16;

        private final Selector selector;
        private final Context  context;

        private PollItem[] items;
        private int        next;
        private int        used;

        private long timeout;

        // When socket is removed from polling, store free slots here
        private LinkedList<Integer> freeSlots;

        /**
         * Class constructor.
         *
         * @param context
         *            a 0MQ context previously created.
         * @param size
         *            the number of Sockets this poller will contain.
         */
        protected Poller(Context context, int size)
        {
            assert (context != null);
            this.context = context;

            selector = context.selector();
            assert (selector != null);

            items = new PollItem[size];
            timeout = -1L;
            next = 0;

            freeSlots = new LinkedList<Integer>();
        }

        /**
         * Class constructor.
         *
         * @param context
         *            a 0MQ context previously created.
         */
        protected Poller(Context context)
        {
            this(context, SIZE_DEFAULT);
        }

        /**
         * Register a Socket for polling on all events.
         *
         * @param socket
         *            the Socket we are registering.
         * @return the index identifying this Socket in the poll set.
         */
        public int register(Socket socket)
        {
            return register(socket, POLLIN | POLLOUT | POLLERR);
        }

        /**
         * Register a Channel for polling on all events.
         *
         * @param channel
         *            the Channel we are registering.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(SelectableChannel channel)
        {
            return register(channel, POLLIN | POLLOUT | POLLERR);
        }

        /**
         * Register a Socket for polling on the specified events.
         *
         * Automatically grow the internal representation if needed.
         *
         * @param socket
         *            the Socket we are registering.
         * @param events
         *            a mask composed by XORing POLLIN, POLLOUT and POLLERR.
         * @return the index identifying this Socket in the poll set.
         */
        public int register(Socket socket, int events)
        {
            return registerInternal(new PollItem(socket, events));
        }

        /**
         * Register a Socket for polling on the specified events.
         *
         * Automatically grow the internal representation if needed.
         *
         * @param channel
         *            the Channel we are registering.
         * @param events
         *            a mask composed by XORing POLLIN, POLLOUT and POLLERR.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(SelectableChannel channel, int events)
        {
            return registerInternal(new PollItem(channel, events));
        }

        /**
         * Register a Channel for polling on the specified events.
         *
         * Automatically grow the internal representation if needed.
         *
         * @param item
         *            the PollItem we are registering.
         * @return the index identifying this Channel in the poll set.
         */
        public int register(PollItem item)
        {
            return registerInternal(item);
        }

        /**
         * Register a Socket for polling on the specified events.
         *
         * Automatically grow the internal representation if needed.
         *
         * @param item the PollItem we are registering.
         * @return the index identifying this Socket in the poll set.
         */
        private int registerInternal(PollItem item)
        {
            int pos = -1;

            if (!freeSlots.isEmpty()) {
                // If there are free slots in our array, remove one
                // from the free list and use it.
                pos = freeSlots.remove();
            }
            else {
                if (next >= items.length) {
                    PollItem[] nitems = new PollItem[items.length + SIZE_INCREMENT];
                    System.arraycopy(items, 0, nitems, 0, items.length);
                    items = nitems;
                }
                pos = next++;
            }

            items[pos] = item;
            used++;
            return pos;
        }

        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param socket
         *          the Socket to be unregistered
         */
        public void unregister(Socket socket)
        {
            unregisterInternal(socket);
        }

        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param channel
         *          the Socket to be unregistered
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
            for (int i = 0; i < next; ++i) {
                PollItem item = items[i];
                if (item == null) {
                    continue;
                }
                if (item.socket == socket || item.getRawSocket() == socket) {
                    items[i] = null;

                    freeSlots.add(i);
                    --used;

                    break;
                }
            }
        }

        /**
         * Get the PollItem associated with an index.
         *
         * @param index
         *            the desired index.
         * @return the PollItem associated with that index (or null).
         */
        public PollItem getItem(int index)
        {
            if (index < 0 || index >= this.next) {
                return null;
            }
            return this.items[index];
        }

        /**
         * Get the socket associated with an index.
         *
         * @param index
         *            the desired index.
         * @return the Socket associated with that index (or null).
         */
        public Socket getSocket(int index)
        {
            if (index < 0 || index >= this.next) {
                return null;
            }
            return items[index].socket;
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
         * @param timeout
         *            the desired poll timeout in milliseconds.
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
            return items.length;
        }

        /**
         * Get the index for the next position in the poll set size.
         *
         * @return the index for the next position in the poll set size.
         */
        public int getNext()
        {
            return this.next;
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
         * Since ZeroMQ 3.0, the timeout parameter is in <i>milliseconds<i>,
         * but prior to this the unit was <i>microseconds</i>.
         *
         * @param tout
         *            the timeout, as per zmq_poll ();
         *            if -1, it will block indefinitely until an event
         *            happens; if 0, it will return immediately;
         *            otherwise, it will wait for at most that many
         *            milliseconds/microseconds (see above).
         *
         * @see "http://api.zeromq.org/3-0:zmq-poll"
         *
         * @return how many objects where signaled by poll ()
         */
        public int poll(long tout)
        {
            if (tout < -1) {
                return 0;
            }
            if (items.length <= 0 || next <= 0) {
                return 0;
            }
            zmq.poll.PollItem[] pollItems = new zmq.poll.PollItem[used];
            for (int i = 0, j = 0; i < next; i++) {
                if (items[i] != null) {
                    pollItems[j++] = items[i].base;
                }
            }

            try {
                return zmq.ZMQ.poll(selector, pollItems, used, tout);
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
         * @param index
         *
         * @return true if the element was signaled.
         */
        public boolean pollin(int index)
        {
            if (index < 0 || index >= this.next) {
                return false;
            }

            return items[index].isReadable();
        }

        /**
         * Check whether the specified element in the poll set was signaled for output.
         *
         * @param index
         *
         * @return true if the element was signaled.
         */
        public boolean pollout(int index)
        {
            if (index < 0 || index >= this.next) {
                return false;
            }

            return items[index].isWritable();
        }

        /**
         * Check whether the specified element in the poll set was signaled for error.
         *
         * @param index
         *
         * @return true if the element was signaled.
         */
        public boolean pollerr(int index)
        {
            if (index < 0 || index >= this.next) {
                return false;
            }

            return items[index].isError();
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

            if (getRawSocket() != null && getRawSocket() == target.getRawSocket()) {
                return true;
            }

            return false;
        }
    }

    public enum Error
    {
        ENOTSUP(ZError.ENOTSUP),
        EPROTONOSUPPORT(ZError.EPROTONOSUPPORT),
        ENOBUFS(ZError.ENOBUFS),
        ENETDOWN(ZError.ENETDOWN),
        EADDRINUSE(ZError.EADDRINUSE),
        EADDRNOTAVAIL(ZError.EADDRNOTAVAIL),
        ECONNREFUSED(ZError.ECONNREFUSED),
        EINPROGRESS(ZError.EINPROGRESS),
        EHOSTUNREACH(ZError.EHOSTUNREACH),
        EMTHREAD(ZError.EMTHREAD),
        EFSM(ZError.EFSM),
        ENOCOMPATPROTO(ZError.ENOCOMPATPROTO),
        ETERM(ZError.ETERM),
        ENOTSOCK(ZError.ENOTSOCK),
        EAGAIN(ZError.EAGAIN);

        private final int code;

        Error(int code)
        {
            this.code = code;
        }

        public int getCode()
        {
            return code;
        }

        public static Error findByCode(int code)
        {
            for (Error e : Error.values()) {
                if (e.getCode() == code) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown " + Error.class.getName() + " enum code:" + code);
        }
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
     *
     * Before calling ZMQ.proxy() you must set any socket options, and connect or bind both frontend and backend sockets.
     * The two conventional proxy models are:
     *
     * ZMQ.proxy() runs in the current thread and returns only if/when the current context is closed.
     * @param frontend ZMQ.Socket
     * @param backend ZMQ.Socket
     * @param capture If the capture socket is not NULL, the proxy shall send all messages, received on both
     *                frontend and backend, to the capture socket. The capture socket should be a
     *                ZMQ_PUB, ZMQ_DEALER, ZMQ_PUSH, or ZMQ_PAIR socket.
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
     *
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

    /**
     * Inner class: Event.
     * Monitor socket event class
     */
    public static class Event
    {
        private final int    event;
        private final Object value;
        private final String address;

        public Event(int event, Object value, String address)
        {
            this.event = event;
            this.value = value;
            this.address = address;
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
         * Receive an event from a monitor socket.
         * @param socket the socket
         * @param flags the flags to apply to the receive operation.
         * @return the received event or null if no message was received.
         * @throws ZMQException
         */
        public static Event recv(Socket socket, int flags)
        {
            zmq.ZMQ.Event e = zmq.ZMQ.Event.read(socket.base, flags);
            return e != null ? new Event(e.event, e.arg, e.addr) : null;
        }

        /**
         * Receive an event from a monitor socket.
         * Does a blocking recv.
         * @param socket the socket
         * @return the received event.
         * @throws ZMQException
         */
        public static Event recv(Socket socket)
        {
            return Event.recv(socket, 0);
        }
    }
}
