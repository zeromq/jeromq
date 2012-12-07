/*
    Copyright (c) 1991-2011 iMatix Corporation <www.imatix.com>
    Copyright other contributors as noted in the AUTHORS file.

                
    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.
            
    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.
        
    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.jeromq;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;

import zmq.Ctx;
import zmq.DecoderBase;
import zmq.EncoderBase;
import zmq.SocketBase;
import zmq.ZError;

public class ZMQ {
    
    /**
     * Socket flag to indicate that more message parts are coming.
     */
    public static final int SNDMORE = zmq.ZMQ.ZMQ_SNDMORE;
    
    // Values for flags in Socket's send and recv functions.
    /**
     * Socket flag to indicate a nonblocking send or recv mode.
     */
    public static final int DONTWAIT = zmq.ZMQ.ZMQ_DONTWAIT;
    public static final int NOBLOCK = zmq.ZMQ.ZMQ_DONTWAIT;
    
    // Socket types, used when creating a Socket.
    /**
     * Flag to specify a exclusive pair of sockets.
     */
    public static final int PAIR = zmq.ZMQ.ZMQ_PAIR;
    /**
     * Flag to specify a PUB socket, receiving side must be a SUB or XSUB.
     */
    public static final int PUB = zmq.ZMQ.ZMQ_PUB;
    /**
     * Flag to specify the receiving part of the PUB or XPUB socket.
     */
    public static final int SUB = zmq.ZMQ.ZMQ_SUB;
    /**
     * Flag to specify a REQ socket, receiving side must be a REP.
     */
    public static final int REQ = zmq.ZMQ.ZMQ_REQ;
    /**
     * Flag to specify the receiving part of a REQ socket.
     */
    public static final int REP = zmq.ZMQ.ZMQ_REP;
    /**
     * Flag to specify a DEALER socket (aka XREQ). 
     * DEALER is really a combined ventilator / sink 
     * that does load-balancing on output and fair-queuing on input 
     * with no other semantics. It is the only socket type that lets 
     * you shuffle messages out to N nodes and shuffle the replies 
     * back, in a raw bidirectional asynch pattern.
     */
    public static final int DEALER = zmq.ZMQ.ZMQ_DEALER;
    /**
    * Old alias for DEALER flag.
    * Flag to specify a XREQ socket, receiving side must be a XREP.
    *
    * @deprecated  As of release 3.0 of zeromq, replaced by {@link #DEALER}
    */
    public static final int XREQ = DEALER;
    /**
     * Flag to specify ROUTER socket (aka XREP).
     * ROUTER is the socket that creates and consumes request-reply 
     * routing envelopes. It is the only socket type that lets you route 
     * messages to specific connections if you know their identities.
     */
    public static final int ROUTER = zmq.ZMQ.ZMQ_ROUTER;
    /**
     * Old alias for ROUTER flag.
     * Flag to specify the receiving part of a XREQ socket.
     *
     * @deprecated  As of release 3.0 of zeromq, replaced by {@link #ROUTER}
     */
    public static final int XREP = ROUTER;
    /**
     * Flag to specify the receiving part of a PUSH socket.
     */
    public static final int PULL = zmq.ZMQ.ZMQ_PULL;
    /**
     * Flag to specify a PUSH socket, receiving side must be a PULL.
     */
    public static final int PUSH = zmq.ZMQ.ZMQ_PUSH;
    /**
     * Flag to specify a XPUB socket, receiving side must be a SUB or XSUB.
     * Subscriptions can be received as a message. Subscriptions start with
     * a '1' byte. Unsubscriptions start with a '0' byte.
     */
    public static final int XPUB = zmq.ZMQ.ZMQ_XPUB;
    /**
     * Flag to specify the receiving part of the PUB or XPUB socket. Allows
     */
    public static final int XSUB = zmq.ZMQ.ZMQ_XSUB;

    /**
     * Flag to specify a STREAMER device.
     */
    public static final int STREAMER = zmq.ZMQ.ZMQ_STREAMER ;
    /**
     * Flag to specify a FORWARDER device.
     */
    public static final int FORWARDER = zmq.ZMQ.ZMQ_FORWARDER ;
    /**
     * Flag to specify a QUEUE device.
     */
    public static final int QUEUE = zmq.ZMQ.ZMQ_QUEUE ;
    
    /**
     * @see ZMQ#PULL
     */
    @Deprecated
    public static final int UPSTREAM = PULL;
    /**
     * @see ZMQ#PUSH
     */
    @Deprecated
    public static final int DOWNSTREAM = PUSH;

    public static final int POLLIN = zmq.ZMQ.ZMQ_POLLIN;
    public static final int POLLOUT = zmq.ZMQ.ZMQ_POLLOUT;
    public static final int POLLERR = zmq.ZMQ.ZMQ_POLLERR;
    
    /**
     * ZMQ Events
     */
    public static final int EVENT_CONNECTED = zmq.ZMQ.ZMQ_EVENT_CONNECTED;
    public static final int EVENT_DELAYED = zmq.ZMQ.ZMQ_EVENT_CONNECT_DELAYED;
    public static final int EVENT_RETRIED = zmq.ZMQ.ZMQ_EVENT_CONNECT_RETRIED;
    public static final int EVENT_CONNECT_FAILED = zmq.ZMQ.ZMQ_EVENT_CONNECT_FAILED;

    public static final int EVENT_LISTENING = zmq.ZMQ.ZMQ_EVENT_LISTENING;
    public static final int EVENT_BIND_FAILED = zmq.ZMQ.ZMQ_EVENT_BIND_FAILED;

    public static final int EVENT_ACCEPTED = zmq.ZMQ.ZMQ_EVENT_ACCEPTED;
    public static final int EVENT_ACCEPT_FAILED = zmq.ZMQ.ZMQ_EVENT_ACCEPT_FAILED;

    public static final int EVENT_CLOSED = zmq.ZMQ.ZMQ_EVENT_CLOSED;
    public static final int EVENT_CLOSE_FAILED = zmq.ZMQ.ZMQ_EVENT_CLOSE_FAILED;
    public static final int EVENT_DISCONNECTED = zmq.ZMQ.ZMQ_EVENT_DISCONNECTED;
    
    public static final int EVENT_ALL = zmq.ZMQ.ZMQ_EVENT_ALL;

    /**
     * Create a new Context.
     * 
     * @param ioThreads
     *            Number of threads to use, usually 1 is sufficient for most use cases.
     * @return the Context
     */
    public static Context context(int ioThreads) {
        return new Context(ioThreads);
    }
    
    public static Context context() {
        return new Context(1);
    }

    public static class Context {

        private final Ctx ctx;
        
        /**
         * Class constructor.
         * 
         * @param ioThreads
         *            size of the threads pool to handle I/O operations.
         */
        protected Context(int ioThreads) {
            ctx = zmq.ZMQ.zmq_init(ioThreads);
        }

        /**
         * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ
         * Context has been disposed of.
         */

        public void term() {
            ctx.terminate();
        }

        /**
         * Create a new Socket within this context.
         * 
         * @param type
         *            the socket type.
         * @return the newly created Socket.
         */
        public Socket socket(int type) {
            return new Socket(this, type);
        }

        /**
         * Create a new Poller within this context, with a default size.
         * 
         * @return the newly created Poller.
         */
        public Poller poller () {
            return new Poller (this);
        }
        
        /**
         * Create a new Poller within this context, with a specified initial size.
         * 
         * @param size
         *            the poller initial size.
         * @return the newly created Poller.
         */
        public Poller poller (int size) {
            return new Poller (this, size);
        }
        
    }
    
    public static class Socket {

        //  This port range is defined by IANA for dynamic or private ports
        //  We use this when choosing a port for dynamic binding.
        private static final int DYNFROM = 0xc000;
        private static final int DYNTO = 0xffff;
        
        private final Ctx ctx;
        private final SocketBase base;

        /**
         * Class constructor.
         * 
         * @param context
         *            a 0MQ context previously created.
         * @param type
         *            the socket type.
         */
        protected Socket(Context context_, int type) {
            ctx = context_.ctx;
            base = ctx.create_socket(type);
            mayRaise();
        }
        
        protected Socket(SocketBase base_) {
            ctx = null;
            base = base_;
        }

        public SocketBase base() {
            return base;
        }
        
        private final static void mayRaise () 
        {
            if (zmq.ZError.is (0) || zmq.ZError.is (zmq.ZError.EAGAIN) ) ;
            else if (zmq.ZError.is (zmq.ZError.ETERM) ) 
                throw new ZMQException.CtxTerminated ();
            else
                throw new ZMQException (zmq.ZError.errno ());

        }
        
        /**
         * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ Socket
         * has been disposed of.
         */
        public final void close() {
            base.close();
            
        }
        
        /**
         * The 'ZMQ_TYPE option shall retrieve the socket type for the specified
         * 'socket'.  The socket type is specified at socket creation time and
         * cannot be modified afterwards.
         *
         * @return the socket type.
         * @since 2.1.0
         */
        public final int getType () {
            return base.getsockopt(zmq.ZMQ.ZMQ_TYPE);
        }
        
        /**
         * @see #setLinger(long)
         *
         * @return the linger period.
         * @since 2.1.0
         */
        public final long getLinger() {
            return base.getsockopt(zmq.ZMQ.ZMQ_LINGER);
        }

        /**
         * The 'ZMQ_LINGER' option shall retrieve the period for pending outbound
         * messages to linger in memory after closing the socket. Value of -1 means
         * infinite. Pending messages will be kept until they are fully transferred to
         * the peer. Value of 0 means that all the pending messages are dropped immediately
         * when socket is closed. Positive value means number of milliseconds to keep
         * trying to send the pending messages before discarding them.
         *
         * @param linger
         *            the linger period.
         * @since 2.1.0
         */
        public final void setLinger (long value) 
        {
            base.setsockopt (zmq.ZMQ.ZMQ_LINGER, (int) value);
        }
        
        /**
         * @see #setReconnectIVL(long)
         *
         * @return the reconnectIVL.
         * @since 3.0.0
         */
        public final long getReconnectIVL() {
            return base.getsockopt(zmq.ZMQ.ZMQ_RECONNECT_IVL);
        }
        
        /**
         * @since 3.0.0
         */
        public final void setReconnectIVL(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RECONNECT_IVL, (int)value);
            mayRaise();
        }
        

        /**
         * @see #setBacklog(long)
         *
         * @return the backlog.
         * @since 3.0.0
         */
        public final long getBacklog() {
            return base.getsockopt(zmq.ZMQ.ZMQ_BACKLOG);
        }
        

        /**
         * @since 3.0.0
         */
        public final void setBacklog(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_BACKLOG, (int)value);
            mayRaise();
        }
        

        /**
         * @see #setReconnectIVLMax(long)
         *
         * @return the reconnectIVLMax.
         * @since 3.0.0
         */
        public final long getReconnectIVLMax () {
            return base.getsockopt(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX);
        }
        
        /**
         * @since 3.0.0
         */
        public final void setReconnectIVLMax (long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX, (int)value);
            mayRaise();
        }
        

        /**
         * @see #setMaxMsgSize(long)
         *
         * @return the maxMsgSize.
         * @since 3.0.0
         */
        public final long getMaxMsgSize() {
            return (Long)base.getsockoptx(zmq.ZMQ.ZMQ_MAXMSGSIZE);
        }
        
        /**
         * @since 3.0.0
         */
        public final void setMaxMsgSize(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_MAXMSGSIZE, value);
            mayRaise();
        }
        

        /**
         * @see #setSndHWM(long)
         *
         * @return the SndHWM.
         * @since 3.0.0
         */
        public final long getSndHWM() {
            return base.getsockopt(zmq.ZMQ.ZMQ_SNDHWM);
        }
        
        /**
         * @since 3.0.0
         */
        public final void setSndHWM(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_SNDHWM, (int)value);
            mayRaise();
        }
        

        /**
         * @see #setRcvHWM(long)
         *
         * @return the recvHWM period.
         * @since 3.0.0
         */
        public final long getRcvHWM() {
            return base.getsockopt(zmq.ZMQ.ZMQ_RCVHWM);
        }
        
        /**
         * @since 3.0.0
         */
        public final void setRcvHWM(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RCVHWM, (int)value);
            mayRaise();
        }
        
        
        /**
         * @see #setHWM(long)
         * 
         * @return the High Water Mark.
         */
        @Deprecated
        public final long getHWM() {
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
         */
        public final void setHWM(long hwm) {
            setSndHWM (hwm);
            setRcvHWM (hwm);
        }
        
        /**
         * @see #setSwap(long)
         * 
         * @return the number of messages to swap at most.
         */
        @Deprecated
        public final long getSwap() {
            // not support at zeromq 3
            return -1L;
        }
        

        /**
         * Get the Swap. The 'ZMQ_SWAP' option shall set the disk offload (swap) size for the
         * specified 'socket'. A socket which has 'ZMQ_SWAP' set to a non-zero value may exceed its
         * high water mark; in this case outstanding messages shall be offloaded to storage on disk
         * rather than held in memory.
         * 
         * @param swap
         *            The value of 'ZMQ_SWAP' defines the maximum size of the swap space in bytes.
         */
        @Deprecated
        public final void setSwap(long value) {
            // not support at zeromq 3
        }


        /**
         * @see #setAffinity(long)
         * 
         * @return the affinity.
         */
        public final long getAffinity() {
            return (Long)base.getsockoptx(zmq.ZMQ.ZMQ_AFFINITY);
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
         * See also  in the man page of zmq_init[3] for details on allocating the number of I/O threads for a
         * specific _context_.
         * 
         * @param affinity
         *            the affinity.
         */
        public final void setAffinity(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_AFFINITY, value);
            mayRaise();
        }
        
        /**
         * @see #setIdentity(byte[])
         * 
         * @return the Identitiy.
         */
        public final byte[] getIdentity() {
            return (byte[]) base.getsockoptx(zmq.ZMQ.ZMQ_IDENTITY);
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
         */
        public final void setIdentity(byte[] identity) {
            base.setsockopt(zmq.ZMQ.ZMQ_IDENTITY, identity);
            mayRaise();
        }
        
        public final void setIdentity(String identity) {
            setIdentity(identity.getBytes());
        }
        /**
         * @see #setRate(long)
         * 
         * @return the Rate.
         */
        public final long getRate() {
            return base.getsockopt(zmq.ZMQ.ZMQ_RATE);
        }
        

        /**
         * The 'ZMQ_RATE' option shall set the maximum send or receive data rate for multicast
         * transports such as  in the man page of zmq_pgm[7] using the specified 'socket'.
         * 
         * @param rate
         */
        public final void setRate(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RATE, (int)value);
            mayRaise();
        }


        /**
         * @see #setRecoveryInterval(long)
         * 
         * @return the RecoveryIntervall.
         */
        public final long getRecoveryInterval () {
            return base.getsockopt(zmq.ZMQ.ZMQ_RECOVERY_IVL);
        }
        

        /**
         * The 'ZMQ_RECOVERY_IVL' option shall set the recovery interval for multicast transports
         * using the specified 'socket'. The recovery interval determines the maximum time in
         * seconds that a receiver can be absent from a multicast group before unrecoverable data
         * loss will occur.
         * 
         * CAUTION: Excersize care when setting large recovery intervals as the data needed for
         * recovery will be held in memory. For example, a 1 minute recovery interval at a data rate
         * of 1Gbps requires a 7GB in-memory buffer. {Purpose of this Method}
         * 
         * @param recovery_ivl
         */
        public final void setRecoveryInterval (long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RECOVERY_IVL, (int)value);
            mayRaise();
        }

        /**
         * @see #setMulticastLoop(boolean)
         * 
         * @return the Multicast Loop.
         */
        @Deprecated
        public final boolean hasMulticastLoop () {
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
         * @param mcast_loop
         */
        @Deprecated
        public final void setMulticastLoop (boolean mcast_loop) {
        }
        
        /**
         * @see #setMulticastHops(long)
         * 
         * @return the Multicast Hops.
         */
        public final long getMulticastHops () {
            return base.getsockopt(zmq.ZMQ.ZMQ_MULTICAST_HOPS);
        }

        
        /**
         * Sets the time-to-live field in every multicast packet sent from this socket.
         * The default is 1 which means that the multicast packets don't leave the local
         * network.
         * 
         * @param mcast_hops
         */
        public final void setMulticastHops (long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_MULTICAST_HOPS, (int)value);
            mayRaise();
        }

        /**
         * @see #setReceiveTimeOut(long)
         * 
         * @return the Receive Timeout
         */
        public final int getReceiveTimeOut() {
            return base.getsockopt(zmq.ZMQ.ZMQ_RCVTIMEO);
        }
        
        /**
         * Sets the timeout for receive operation on the socket. If the value is 0, recv 
         * will return immediately, with null if there is no message to receive. 
         * If the value is -1, it will block until a message is available. For all other 
         * values, it will wait for a message for that amount of time before returning with
         * an null.
         * 
         * @param timeout
         */
        public final void setReceiveTimeOut(int value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RCVTIMEO, value);
            mayRaise();
        }
        

        /**
         * @see #setSendTimeOut(long)
         * 
         * @return the Send Timeout.
         */
        public final int getSendTimeOut() {
            return (int)base.getsockopt(zmq.ZMQ.ZMQ_SNDTIMEO);
        }
        
        /**
         * Sets the timeout for send operation on the socket. If the value is 0, send
         * will return immediately, with a false if the message cannot be sent.
         * If the value is -1, it will block until the message is sent. For all other
         * values, it will try to send the message for that amount of time before
         * returning with a false.
         * 
         * @param timeout
         */
        public final void setSendTimeOut(int value) {
            base.setsockopt(zmq.ZMQ.ZMQ_SNDTIMEO, value);
            mayRaise();
        }
        

        /**
         * @see #setSendBufferSize(long)
         * 
         * @return the kernel send buffer size.
         */
        public final long getSendBufferSize() {
            return base.getsockopt(zmq.ZMQ.ZMQ_SNDBUF);
        }
        
        /**
         * The 'ZMQ_SNDBUF' option shall set the underlying kernel transmit buffer size for the
         * 'socket' to the specified size in bytes. A value of zero means leave the OS default
         * unchanged. For details please refer to your operating system documentation for the
         * 'SO_SNDBUF' socket option.
         * 
         * @param sndbuf
         */
        public final void setSendBufferSize(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_SNDBUF, (int)value);
            mayRaise();
        }
        
        /**
         * @see #setReceiveBufferSize(long)
         * 
         * @return the kernel receive buffer size.
         */
        public final long getReceiveBufferSize() {
            return base.getsockopt(zmq.ZMQ.ZMQ_RCVBUF);
        }
        
        /**
         * The 'ZMQ_RCVBUF' option shall set the underlying kernel receive buffer size for the
         * 'socket' to the specified size in bytes. A value of zero means leave the OS default
         * unchanged. For details refer to your operating system documentation for the 'SO_RCVBUF'
         * socket option.
         * 
         * @param rcvbuf
         */
        public final void setReceiveBufferSize(long value) {
            base.setsockopt(zmq.ZMQ.ZMQ_RCVBUF, (int)value);
            mayRaise();
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
        public final boolean hasReceiveMore () 
        {
            return base.getsockopt (zmq.ZMQ.ZMQ_RCVMORE) == 1;
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
         * @since 2.1.0
         */
        public final SelectableChannel getFD() {
            return (SelectableChannel)base.getsockoptx(zmq.ZMQ.ZMQ_FD);
        }
        
        /**
         * The 'ZMQ_EVENTS' option shall retrieve event flags for the specified socket.
         * If a message can be read from the socket ZMQ_POLLIN flag is set. If message can
         * be written to the socket ZMQ_POLLOUT flag is set.
         * 
         * @return the mask of outstanding events.
         * @since 2.1.0
         */
        public final int getEvents() {
            return base.getsockopt(zmq.ZMQ.ZMQ_EVENTS);
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
         */
        public final void subscribe(byte[] topic) {
            base.setsockopt(zmq.ZMQ.ZMQ_SUBSCRIBE, topic);
            mayRaise();
        }
        
        public final void subscribe(String topic) {
            subscribe(topic.getBytes());
        }
        

        /**
         * The 'ZMQ_UNSUBSCRIBE' option shall remove an existing message filter on a 'ZMQ_SUB'
         * socket. The filter specified must match an existing filter previously established with
         * the 'ZMQ_SUBSCRIBE' option. If the socket has several instances of the same filter
         * attached the 'ZMQ_UNSUBSCRIBE' option shall remove only one instance, leaving the rest in
         * place and functional.
         * 
         * @param topic
         */        
        public final void unsubscribe(byte[] topic) {
            base.setsockopt(zmq.ZMQ.ZMQ_UNSUBSCRIBE, topic);
            mayRaise();
        }
        
        public final void unsubscribe(String topic) {
            unsubscribe(topic.getBytes());
        }

        
        /**
         * Set custom Encoder
         * @param cls
         */
        public final void setEncoder(Class<? extends EncoderBase> cls) {
            base.setsockopt(zmq.ZMQ.ZMQ_ENCODER, cls);
        }
        
        /**
         * Set custom Decoder
         * @param cls
         */
        public final void setDecoder(Class<? extends DecoderBase> cls) {
            base.setsockopt(zmq.ZMQ.ZMQ_DECODER, cls);
        }


        /**
         * @see #setRouterMandatory (boolean)
         * 
         * @return the Router Manadatory.
         */
        public final boolean getRouterMandatory () {
            return false;
        }
        
        
        /**
         * Set Router Mandatory
         * @param mandadatory
         */
        public final void setRouterMandatory (boolean mandatory) {
            base.setsockopt (zmq.ZMQ.ZMQ_ROUTER_MANDATORY, mandatory ? 1 : 0);
        }
        
        /**
         * Bind to network interface. Start listening for new connections.
         * 
         * @param addr
         *            the endpoint to bind to.
         */
        public final int bind (String addr) 
        {
            return bind (addr, DYNFROM, DYNTO);
        }

        /**
         * Bind to network interface. Start listening for new connections.
         * 
         * @param addr
         *            the endpoint to bind to.
         * @param min
         *            The minimum port in the range of ports to try.
         * @param max
         *            The maximum port in the range of ports to try.
         */
        private final int bind (String addr, int min, int max) 
        {
            if (addr.endsWith (":*")) {
                int port = min;
                String prefix = addr.substring (0, addr.lastIndexOf (':') + 1);
                while (port <= max) {
                    addr = prefix + port;
                    //  Try to bind on the next plausible port
                    if (base.bind (addr))
                        return port;
                    port++;
                }
                return -1;
            } else {
                if (base.bind(addr)) {
                    int port = 0;
                    try {
                        port = Integer.parseInt (
                            addr.substring (addr.lastIndexOf (':') + 1));
                    } catch (NumberFormatException e) {
                    }
                    return port;
                } else {
                    return -1;
                }
            }
        }
        
        /**
         * Bind to network interface to a random port. Start listening for new
         * connections.
         *
         * @param addr
         *            the endpoint to bind to.
         */
        public int bindToRandomPort (String addr) 
        {
            return bind (addr + ":*", DYNFROM, DYNTO);
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
        public int bindToRandomPort (String addr, int min, int max) 
        {
            return bind (addr + ":*", min, max);
        }

        /**
         * Connect to remote application.
         * 
         * @param addr
         *            the endpoint to connect to.
         */
        public final boolean connect(String addr_) {
            return base.connect(addr_);
        }

        public final boolean send (String data) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, 0);
        }
        
        public final boolean sendMore (String data) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, zmq.ZMQ.ZMQ_SNDMORE);
        }
        
        public final boolean send (String data, int flags) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, flags);
        }
        
        public final boolean send (byte[] data) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, 0);
        }
        
        public final boolean sendMore (byte[] data) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, zmq.ZMQ.ZMQ_SNDMORE);
        }

        
        public final boolean send (byte[] data, int flags) {
            zmq.Msg msg = new zmq.Msg(data);
            return base.send(msg, flags);
        }
        
        /**
         * Send a message.
         * 
         * @param msg
         *            the message to send, as an array of bytes.
         * @return true if send was successful, false otherwise.
         */
        public final boolean send (Msg msg) {
            return base.send(msg.base, 0);
        }
        
        /**
         * Send a message.
         * 
         * @param msg
         *            the message to send, as an array of bytes.
         * @return true if send was successful, false otherwise.
         */
        public final boolean sendMore (Msg msg) {
            return base.send(msg.base, zmq.ZMQ.ZMQ_SNDMORE);
        }

         
        /**
        * Send a message.
        * 
        * @param msg
        *            the message to send, as an array of bytes.
        * @param flags
        *            the flags to apply to the send operation.
        * @return true if send was successful, false otherwise.
        */
        public final boolean send (Msg msg, int flags) {
            return base.send(msg.base, flags);
        }

        /**
         * Receive a message.
         * 
         * @return the message received, as an array of bytes; null on error.
         */
        public final byte [] recv () 
        {
            return recv (0);
        }
        
        /**
         * Receive a message.
         * 
         * @param flags
         *            the flags to apply to the receive operation.
         * @return the message received, as an array of bytes; null on error.
         */
        public final byte [] recv (int flags) 
        {
            zmq.Msg msg = base.recv(flags);
            
            if (msg != null) {
                return msg.data();
            }
            
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
        public final int recv (byte [] buffer, int offset, int len, int flags) 
        {
            zmq.Msg msg = base.recv(flags);
            
            if (msg != null) {
                System.arraycopy(msg.data(), 0, buffer, offset, len);
                return msg.size();
            }
            
            return -1;
        }
        
        /**
         * Receive a message.
         * 
         * @return the message received, as a Msg object; null on no message.
         */
        public final Msg recvMsg () 
        {
            return recvMsg (0);
        }
        
        /**
         * Receive a message.
         * 
         * @param flags
         *            the flags to apply to the receive operation.
         * @return the message received, as a Msg object; null on no message.
         */
        public final Msg recvMsg (int flags) 
        {
            zmq.Msg msg = base.recv(flags);
            
            if (msg != null) {
                return new Msg (msg);
            }
            
            return null;
        }
        
        /**
         * 
         * @return the message received, as a String object; null on no message.
         */
        public final String recvStr () {
            return recvStr (0);
        }
        
        /**
         * 
         * @param flags the flags to apply to the receive operation.
         * @return the message received, as a String object; null on no message.
         */
        public final String recvStr (int flags) 
        {
            zmq.Msg msg = base.recv(flags);
            
            if (msg != null) {
                return new String (msg.data());
            }
            
            return null;
        }
        
        public boolean monitor (String addr, int events) 
        {
            return base.monitor (addr, events);
        }

        public void dump() {
            System.out.println("----------------------------------------");
            while(true) {
                Msg msg = recvMsg(0);
                System.out.println(String.format("[%03d] %s", msg.size(), 
                        msg.size() > 0 ? new String(msg.data()) : ""));
                if (!hasReceiveMore()) {
                    break;
                }
            }
        }


    }

    public static class Msg {
        
        public final static int MORE = zmq.Msg.more;
        private final zmq.Msg base;
        
        public Msg () {
            base = new zmq.Msg (); //empty message for bottom
        }
        
        public Msg (zmq.Msg msg) {
            base = msg;
        }
        
        public Msg (int size) {
            base = new zmq.Msg (size);
        }

        public Msg (String data) {
            base = new zmq.Msg (data);
        }
        
        public Msg (ByteBuffer data) {
            base = new zmq.Msg (data);
        }

        public int size () {
            return base.size ();
        }

        public ByteBuffer buf () {
            return base.buf ();
        }
        
        public byte [] data () {
            return base.data ();
        }

        public void put (String data, int idx) {
            base.put (data, idx);
        }

        public void put (byte [] data, int idx) {
            base.put (data, idx);
        }
        
        public void put (Msg data, int idx) {
            base.put (data.base, idx);
        }
        
        public void setFlags (int flags) {
            base.set_flags (flags);
        }

        public int getFlags ()
        {
            return base.flags ();
        }

        public boolean hasMore ()
        {
            return base.has_more ();
        }
        
        public void close ()
        {
            base.close ();
        }

    }
    
    // GC closes selector handle
    protected static class ReuseableSelector {

        private Selector selector;
        
        protected ReuseableSelector () {
            selector = null;
        }
        
        public Selector open () throws IOException
        {
            selector = Selector.open();
            return selector;
        }
        
        public Selector get () 
        {
            assert (selector != null);
            assert (selector.isOpen ());
            return selector;
        }

        @Override
        public void finalize ()
        {
            try {
                selector.close ();
            } catch (IOException e) {
            }
            try {
                super.finalize ();
            } catch (Throwable e) {
            }
        }
        
    }
    
    public static class Poller {

        /**
         * These values can be ORed to specify what we want to poll for.
         */
        public static final int POLLIN = zmq.ZMQ.ZMQ_POLLIN;
        public static final int POLLOUT = zmq.ZMQ.ZMQ_POLLOUT;
        public static final int POLLERR = zmq.ZMQ.ZMQ_POLLERR;
        
        private static final int SIZE_DEFAULT = 32;
        private static final int SIZE_INCREMENT = 16;
        
        private static final ThreadLocal <ReuseableSelector> holder = new ThreadLocal <ReuseableSelector> ();
        private Selector selector;
        private zmq.PollItem [] items;
        private long timeout;
        private int next;

        /**
         * Class constructor.
         * 
         * @param context
         *            a 0MQ context previously created.
         * @param size
         *            the number of Sockets this poller will contain.
         */
        protected Poller (Context context, int size) {
            items = new zmq.PollItem[size];
            timeout = -1L;
            next = 0;
            open ();
        }

        /**
         * Class constructor.
         * 
         * @param context
         *            a 0MQ context previously created.
         */
        protected Poller (Context context) {
            this(context, SIZE_DEFAULT);
        }
        
        private void open () {
            if (holder.get () == null) {
                synchronized (holder) {
                    try {
                       if (selector == null) {
                           ReuseableSelector s = new ReuseableSelector ();
                           selector = s.open ();
                           holder.set (s);
                       }
                    } catch (IOException e) {
                        throw new ZError.IOException(e);
                    }
                }
            }
            selector = holder.get ().get ();
        }
        
        /**
         * Register a Socket for polling on all events.
         * 
         * @param socket
         *            the Socket we are registering.
         * @return the index identifying this Socket in the poll set.
         */
        public int register (Socket socket) {
            return register (socket, POLLIN | POLLOUT | POLLERR);
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
        public int register (Socket socket, int events) {

            return insert (new zmq.PollItem (socket.base, events));
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
        public int register (SelectableChannel channel, int events) {

            return insert (new zmq.PollItem (channel, events));
        }

        private int insert (zmq.PollItem item)
        {
            int pos = next++;
            if (pos == items.length) {
                zmq.PollItem[] nitems = new zmq.PollItem[items.length + SIZE_INCREMENT];
                System.arraycopy (items, 0, nitems, 0, items.length);
                items = nitems;
            }
            items [pos] = item;
            return pos;
        }
        
        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param socket 
         *          the Socket to be unregistered
         */
        public void unregister (Socket socket) {
            for (int pos = 0; pos < items.length ;pos++) {
                zmq.PollItem item = items[pos];
                if (item.socket () == socket.base) {
                    remove (pos);
                    break;
                }
            }
        }
        
        /**
         * Unregister a Socket for polling on the specified events.
         *
         * @param channel 
         *          the Socket to be unregistered
         */
        public void unregister (SelectableChannel channel) {
            for (int pos = 0; pos < items.length ;pos++) {
                zmq.PollItem item = items[pos];
                if (item.getChannel () == channel) {
                    remove (pos);
                    break;
                }
            }
        }

        private void remove (int pos)
        {
            next--;
            if (pos != next)
                items [pos] = items [next];
            items [next] = null;
        }
        
        /**
         * Get the socket associated with an index.
         * 
         * @param index
         *            the desired index.
         * @return the Socket associated with that index (or null).
         */
        public Socket getSocket (int index) {
            if (index < 0 || index >= this.next)
                return null;
            return new Socket(items [index].socket());
        }
        
        /**
         * Get the current poll timeout.
         * 
         * @return the current poll timeout in microseconds.
         * @deprecated Timeout handling has been moved to the poll() methods.
         */
        public long getTimeout () {
            return this.timeout;
        }

        /**
         * Set the poll timeout.
         * 
         * @param timeout
         *            the desired poll timeout in microseconds.
         * @deprecated Timeout handling has been moved to the poll() methods.
         */
        public void setTimeout (long timeout) {
            if (timeout < -1)
                return;

            this.timeout = timeout;
        }

        /**
         * Get the current poll set size.
         * 
         * @return the current poll set size.
         */
        public int getSize () {
            return items.length;
        }

        /**
         * Get the index for the next position in the poll set size.
         * 
         * @return the index for the next position in the poll set size.
         */
        public int getNext () {
            return this.next;
        }
        
        /**
         * Issue a poll call. If the poller's internal timeout value
         * has been set, use that value as timeout; otherwise, block
         * indefinitely.
         * 
         * @return how many objects where signaled by poll ().
         */
        public int poll () {
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
         * @see http://api.zeromq.org/3-0:zmq-poll
         *
         * @return how many objects where signaled by poll ()
         */
        public int poll(long tout) {
            return zmq.ZMQ.zmq_poll (selector, items, tout);
        }
        
        /**
         * Check whether the specified element in the poll set was signaled for input.
         * 
         * @param index
         * 
         * @return true if the element was signaled.
         */
        public boolean pollin (int index) {
            if (index < 0 || index >= this.next) 
                return false;
            
            return items [index].isReadable(); 
        }

        /**
         * Check whether the specified element in the poll set was signaled for output.
         * 
         * @param index
         * 
         * @return true if the element was signaled.
         */
        public boolean pollout (int index) {
            
            if (index < 0 || index >= this.next)
                return false;
            
            return items [index].isWriteable(); 
        }

        /**
         * Check whether the specified element in the poll set was signaled for error.
         * 
         * @param index
         * 
         * @return true if the element was signaled.
         */
        public boolean pollerr (int index) {
            
            if (index < 0 || index >= this.next)
                return false;
            
            return items [index].isError();
        }
    }
    
    public static class PollItem {

        private final zmq.PollItem base;
        
        public PollItem (Socket s, int ops) {
            base = new zmq.PollItem (s.base, ops); 
        }

        public PollItem (SelectableChannel s, int ops) {
            base = new zmq.PollItem (s, ops); 
        }

        public final zmq.PollItem base() {
            return base;
        }

        public final SelectableChannel getChannel() {
            return base.getChannel();
        }

        public final SocketBase getSocket() {
            return base.getSocket();
        }

        public final boolean isReadable() {
            return base.isReadable();
        }
        
        public final boolean isWritable() {
            return base.isWriteable();
        }

        public final int interestOps(int ops) {
            return base.interestOps(ops);
        }
        
    }

    public enum Error {
        
        ENOTSUP(ZError.ENOTSUP),
        EPROTONOSUPPORT(ZError.EPROTONOSUPPORT),
        ENOBUFS(ZError.ENOBUFS),
        ENETDOWN(ZError.ENETDOWN),
        EADDRINUSE(ZError.EADDRINUSE),
        EADDRNOTAVAIL(ZError.EADDRNOTAVAIL),
        ECONNREFUSED(ZError.ECONNREFUSED),
        EINPROGRESS(ZError.EINPROGRESS),
        EMTHREAD(ZError.EMTHREAD),
        EFSM(ZError.EFSM),
        ENOCOMPATPROTO(ZError.ENOCOMPATPROTO),
        ETERM(ZError.ETERM);

        private final long code;

        Error(long code) {
            this.code = code;
        }

        public long getCode() {
            return code;
        }

        public static Error findByCode(int code) {
            for (Error e : Error.class.getEnumConstants()) {
                if (e.getCode() == code) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown " + Error.class.getName() + " enum code:" + code);
        }
    }
    
    public static boolean device (int type_, Socket sa, Socket sb) 
    {
        return zmq.ZMQ.zmq_proxy (sa.base, sb.base, null);
    }
    
    public static int poll (PollItem[] items, long timeout) {
        
        zmq.PollItem[] items_ = new zmq.PollItem[items.length];
        for (int i=0; i < items.length; i++) {
            items_[i] = items[i].base;
        }
        
        return zmq.ZMQ.zmq_poll(items_, timeout);
    }
    

    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getMajorVersion ()
    {
      return zmq.ZMQ.ZMQ_VERSION_MAJOR;
    }
    
    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getMinorVersion ()
    {
      return zmq.ZMQ.ZMQ_VERSION_MINOR;
    }
    
    /**
     * @return Major version number of the ZMQ library.
     */
    public static int getPatchVersion ()
    {
      return zmq.ZMQ.ZMQ_VERSION_PATCH;
    }
    
    /**
     * @return Full version number of the ZMQ library used for comparing versions.
     */
    public static int getFullVersion() {
        return zmq.ZMQ.ZMQ_MAKE_VERSION(zmq.ZMQ.ZMQ_VERSION_MAJOR, 
                zmq.ZMQ.ZMQ_VERSION_MINOR, 
                zmq.ZMQ.ZMQ_VERSION_PATCH);
    }

    /**
     * @param major Version major component.
     * @param minor Version minor component.
     * @param patch Version patch component.
     * 
     * @return Comparible single int version number.
     */
    public static int makeVersion ( final int major,
                                    final int minor,
                                    final int patch )
    {
      return zmq.ZMQ.ZMQ_MAKE_VERSION ( major, minor, patch );
    }
    
    /**
     * @return String version number in the form major.minor.patch.
     */
    public static String getVersionString() {
        return "" + zmq.ZMQ.ZMQ_VERSION_MAJOR + "." +
                zmq.ZMQ.ZMQ_VERSION_MINOR + "." +
                zmq.ZMQ.ZMQ_VERSION_PATCH;
    }



}
