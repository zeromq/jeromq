package org.jeromq.api;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import zmq.DecoderBase;
import zmq.EncoderBase;

import java.nio.channels.SelectableChannel;
import java.util.EnumSet;

public class Socket {
    private final ZMQ.Socket socket;
    private final ZContext zContext;

    Socket(ZMQ.Socket socket, ZContext zContext) {
        this.socket = socket;
        this.zContext = zContext;
    }

    ZMQ.Socket getDelegate() {
        return socket;
    }

    /**
     * Connect to remote endpoint.
     *
     * @param address the endpoint to connect to.
     */
    public boolean connect(String address) {
        return socket.connect(address);
    }

    /**
     * Bind to network interface. Start listening for new connections.
     *
     * @param address the endpoint to bind to.
     */
    public int bind(String address) {
        return socket.bind(address);
    }

    /**
     * Bind to network interface to a random port. Start listening for new
     * connections.
     *
     * @param address the endpoint to bind to.
     * @param min     The minimum port in the range of ports to try.
     * @param max     The maximum port in the range of ports to try.
     */
    public int bindToRandomPort(String address, int min, int max) {
        return socket.bindToRandomPort(address, min, max);
    }

    /**
     * Bind to network interface to a random port. Start listening for new
     * connections.
     *
     * @param address the endpoint to bind to.
     */
    public int bindToRandomPort(String address) {
        return socket.bindToRandomPort(address);
    }

    /**
     * This is an explicit "destructor". It can be called to ensure the corresponding 0MQ Socket
     * has been disposed of.
     */
    public void close() {
        zContext.destroySocket(socket);
    }

    /**
     * Receive a message.
     *
     * @return the message received, as an array of bytes; null on error.
     *         todo: do we really return null on error, or is an exception thrown?
     */
    public byte[] receive() {
        return socket.recv();
    }

    /**
     * Receive a message.
     *
     * @param option option to apply to the receive operation.
     * @return the message received, as an array of bytes; null on error.
     *         todo: do we really return null on error, or is an exception thrown?
     */
    public byte[] receive(SendReceiveOption option) {
        return socket.recv(option.getCValue());
    }

    /**
     * Receive a message in to a specified buffer.
     *
     * @param buffer byte[] to copy zmq message payload in to.
     * @param offset offset in buffer to write data
     * @param len    Maximum bytes to write to the buffer.
     *               If len is smaller than the incoming message size,
     *               the message will be truncated.
     * @param option the flags to apply to the receive operation.
     *               todo: do we really return -1 on error, or is an exception thrown?
     * @return the number of bytes read, -1 on error
     */
    public int receive(byte[] buffer, int offset, int len, SendReceiveOption option) {
        return socket.recv(buffer, offset, len, option.getCValue());
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the byte[] into a String.</b>
     *
     * @return the message received, as a String object; null on no message.
     */
    public String receiveString() {
        return socket.recvStr();
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the byte[] into a String.</b>
     *
     * @param option option to apply to the receive operation.
     * @return the message received, as a String object; null on no message.
     */
    public String receiveString(SendReceiveOption option) {
        return socket.recvStr(option.getCValue());
    }

    /**
     * The 'ZMQ_RCVMORE' option shall return a boolean value indicating if the multi-part
     * message currently being read from the specified 'socket' has more frames to
     * follow. If there are no frames to follow or if the message currently being read is
     * not a multi-part message, false shall be returned. Otherwise, a true shall
     * be returned.
     *
     * @return true if there are more frames to receive.
     */
    public boolean hasMoreFramesToReceive() {
        return socket.hasReceiveMore();
    }

    /**
     * The 'ZMQ_SUBSCRIBE' option shall establish a new message filter on a 'ZMQ_SUB' socket.
     * Newly created 'ZMQ_SUB' sockets shall filter out all incoming messages, therefore you
     * should call this option to establish an initial message filter.
     * <p/>
     * An empty topic of length zero shall subscribe to all incoming messages. A
     * non-empty topic shall subscribe to all messages beginning with the specified
     * prefix. Mutiple filters may be attached to a single 'ZMQ_SUB' socket, in which case a
     * message shall be accepted if it matches at least one filter.
     */
    public void subscribe(byte[] topic) {
        socket.subscribe(topic);
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the String into byte[].</b>
     *
     * @see #subscribe(byte[])
     */
    public void subscribe(String topic) {
        subscribe(topic.getBytes());
    }

    /**
     * The 'ZMQ_UNSUBSCRIBE' option shall remove an existing message filter on a 'ZMQ_SUB'
     * socket. The filter specified must match an existing filter previously established with
     * the 'ZMQ_SUBSCRIBE' option. If the socket has several instances of the same filter
     * attached the 'ZMQ_UNSUBSCRIBE' option shall remove only one instance, leaving the rest in
     * place and functional.
     */
    public void unsubscribe(byte[] topic) {
        socket.unsubscribe(topic);
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the String into byte[].</b>
     *
     * @see #unsubscribe(byte[])
     */
    public void unsubscribe(String topic) {
        unsubscribe(topic.getBytes());
    }

    public boolean send(byte[] data) {
        return socket.send(data);
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the String into byte[].</b>
     */
    public boolean send(String data) {
        return socket.send(data);
    }

    public boolean send(byte[] data, SendReceiveOption option) {
        return socket.send(data, option.getCValue());
    }

    /**
     * <b>WARNING: this method uses the default encoding to turn the String into byte[].</b>
     */
    public boolean send(String data, SendReceiveOption option) {
        return socket.send(data, option.getCValue());
    }

    /**
     * Send data, but mark that additional frames will be sent subsequently.
     * <br/>todo: can we ever return false without an exception?
     *
     * @return true if successful, false otherwise.
     */
    public boolean sendWithMoreExpected(byte[] data) {
        return socket.sendMore(data);
    }

    /**
     * Send data, but mark that additional frames will be sent subsequently.
     * <br/><b>WARNING: this method uses the default encoding to turn the String into byte[].</b>
     * <br/>todo: can we ever return false without an exception?
     *
     * @return true if successful, false otherwise.
     */
    public boolean sendWithMoreExpected(String data) {
        return socket.sendMore(data);
    }

    public boolean monitor(String address, EnumSet<TransportEvent> events) {
        return socket.monitor(address, TransportEvent.getCValue(events));
    }

    /**
     * The 'ZMQ_EVENTS' option shall retrieve event flags for the specified socket.
     * If a message can be read with blocking from the socket, ZMQ_POLLIN flag is set. If message can
     * be written to the socket without blocking, ZMQ_POLLOUT flag is set.
     *
     * @return if ZMQ_POLLOUT is set on the socket.
     */
    public boolean canWriteWithoutBlocking() {
        return (socket.getEvents() & ZMQ.POLLOUT) > 0;
    }

    /**
     * The 'ZMQ_EVENTS' option shall retrieve event flags for the specified socket.
     * If a message can be read from the socket ZMQ_POLLIN flag is set. If message can
     * be written to the socket ZMQ_POLLOUT flag is set.
     *
     * @return if ZMQ_POLLIN is set on the socket.
     */
    public boolean canReadWithoutBlocking() {
        return (socket.getEvents() & ZMQ.POLLIN) > 0;
    }

    /**
     * Dumps everything on the socket to System.out.
     */
    public void dumpToConsole() {
        socket.dump();
    }

    public SocketType getType() {
        return SocketType.typeOf(socket.getType());
    }

    /**
     * The 'ZMQ_RECOVERY_IVL' option shall set the recovery interval for multicast transports
     * using the specified 'socket'. The recovery interval determines the maximum time in
     * seconds that a receiver can be absent from a multicast group before unrecoverable data
     * loss will occur.
     * <p/>
     * CAUTION: Exercise care when setting large recovery intervals as the data needed for
     * recovery will be held in memory. For example, a 1 minute recovery interval at a data rate
     * of 1 Gbps requires a 7GB in-memory buffer. {Purpose of this Method}
     */
    public void setRecoveryInterval(long value) {
        socket.setRecoveryInterval(value);
    }

    /**
     * @see #setRecoveryInterval(long)
     */
    public long getRecoveryInterval() {
        return socket.getRecoveryInterval();
    }

    public void setReconnectInterval(long value) {
        socket.setReconnectIVL(value);
    }

    public long getReconnectInterval() {
        return socket.getReconnectIVL();
    }

    public void setMaxReconnectInterval(long value) {
        socket.setReconnectIVLMax(value);
    }

    public long getMaxReconnectInterval() {
        return socket.getReconnectIVLMax();
    }

    public void setMaxMessageSize(long value) {
        socket.setMaxMsgSize(value);
    }

    public long getMaxMessageSize() {
        return socket.getMaxMsgSize();
    }

    /**
     * The 'ZMQ_LINGER' option shall retrieve the period for pending outbound
     * messages to linger in memory after closing the socket. Value of -1 means
     * infinite. Pending messages will be kept until they are fully transferred to
     * the peer. Value of 0 means that all the pending messages are dropped immediately
     * when socket is closed. Positive value means number of milliseconds to keep
     * trying to send the pending messages before discarding them.
     *
     * @param value the linger period.
     */
    public void setLinger(long value) {
        socket.setLinger(value);
    }

    /**
     * @see #setLinger(long)
     */
    public long getLinger() {
        return socket.getLinger();
    }

    /**
     * The 'ZMQ_RATE' option shall set the maximum send or receive data rate for multicast
     * transports such as  in the man page of zmq_pgm[7] using the specified 'socket'.
     */
    public void setRate(long value) {
        socket.setRate(value);
    }

    /**
     * @see #setRate(long)
     */
    public long getRate() {
        return socket.getRate();
    }

    public void setReceiveHighWaterMark(long value) {
        socket.setRcvHWM(value);
    }

    public long getReceiveHighWaterMark() {
        return socket.getRcvHWM();
    }

    public void setSendHighWaterMark(long value) {
        socket.setSndHWM(value);
    }

    public long getSendHighWaterMark() {
        return socket.getSndHWM();
    }

    /**
     * Sets both the send and receive high water marks to the specified value.
     */
    public void setHighWaterMarks(long highWaterMark) {
        socket.setHWM(highWaterMark);
    }

    /**
     * The 'ZMQ_AFFINITY' option shall set the I/O thread affinity for newly
     * created connections on the specified 'socket'.
     * <p/>
     * Affinity determines which threads from the 0MQ I/O thread pool associated with the
     * socket's _context_ shall handle newly created connections. A value of zero specifies no
     * affinity, meaning that work shall be distributed fairly among all 0MQ I/O threads in the
     * thread pool. For non-zero values, the lowest bit corresponds to thread 1, second lowest
     * bit to thread 2 and so on. For example, a value of 3 specifies that subsequent
     * connections on 'socket' shall be handled exclusively by I/O threads 1 and 2.
     * <p/>
     * See also  in the man page of zmq_init[3] for details on allocating the number of I/O threads for a
     * specific _context_.
     */
    public void setAffinity(long value) {
        socket.setAffinity(value);
    }

    /**
     * @see #setAffinity(long)
     */
    public long getAffinity() {
        return socket.getAffinity();
    }

    /**
     * Sets the timeout for receive operation on the socket. If the value is 0, recv
     * will return immediately, with null if there is no message to receive.
     * If the value is -1, it will block until a message is available. For all other
     * values, it will wait for a message for that amount of time before returning with
     * a null and an EAGAIN error.
     *
     * @param milliseconds Timeout for receive operation in milliseconds. Default -1 (infinite)
     */
    public void setReceiveTimeOut(int milliseconds) {
        socket.setReceiveTimeOut(milliseconds);
    }

    /**
     * @see #setReceiveTimeOut(int)
     */
    public int getReceiveTimeOut() {
        return socket.getReceiveTimeOut();
    }

    /**
     * Sets the timeout for send operation on the socket. If the value is 0, send
     * will return immediately, with a false if the message cannot be sent.
     * If the value is -1, it will block until the message is sent. For all other
     * values, it will try to send the message for that amount of time before
     * returning with false and an EAGAIN error.
     *
     * @param milliseconds Timeout for send operation in milliseconds. Default -1 (infinite)
     */
    public void setSendTimeOut(int milliseconds) {
        socket.setSendTimeOut(milliseconds);
    }

    /**
     * @see #setSendTimeOut(int)
     */
    public int getSendTimeOut() {
        return socket.getSendTimeOut();
    }

    /**
     * The 'ZMQ_SNDBUF' option shall set the underlying kernel transmit buffer size for the
     * 'socket' to the specified size in bytes. A value of zero means leave the OS default
     * unchanged. For details please refer to your operating system documentation for the
     * 'SO_SNDBUF' socket option.
     */
    public void setSendBufferSize(long value) {
        socket.setSendBufferSize(value);
    }

    /**
     * @see #setSendBufferSize(long)
     */
    public long getSendBufferSize() {
        return socket.getSendBufferSize();
    }

    /**
     * The 'ZMQ_RCVBUF' option shall set the underlying kernel receive buffer size for the
     * 'socket' to the specified size in bytes. A value of zero means leave the OS default
     * unchanged. For details refer to your operating system documentation for the 'SO_RCVBUF'
     * socket option.
     */
    public void setReceiveBufferSize(long value) {
        socket.setReceiveBufferSize(value);
    }

    /**
     * @see #setReceiveBufferSize(long)
     */
    public long getReceiveBufferSize() {
        return socket.getReceiveBufferSize();
    }

    /**
     * Sets the identity of the specified 'socket'. Socket
     * identity determines if existing 0MQ infrastructure (_message queues_, _forwarding
     * devices_) shall be identified with a specific application and persist across multiple
     * runs of the application.
     * <p/>
     * If the socket has no identity, each run of an application is completely separate from
     * other runs. However, with identity set the socket shall re-use any existing 0MQ
     * infrastructure configured by the previous run(s). Thus the application may receive
     * messages that were sent in the meantime, _message queue_ limits shall be shared with
     * previous run(s) and so on.
     * <p/>
     * Identity should be at least one byte and at most 255 bytes long. Identities starting with
     * binary zero are reserved for use by 0MQ infrastructure.
     * <p/>
     * <b>WARNING: Default encoding is used to convert the String into byte[]</b>
     */
    public void setIdentity(String identity) {
        socket.setIdentity(identity);
    }

    /**
     * Sets the identity of the specified 'socket'. Socket
     * identity determines if existing 0MQ infrastructure (_message queues_, _forwarding
     * devices_) shall be identified with a specific application and persist across multiple
     * runs of the application.
     * <p/>
     * If the socket has no identity, each run of an application is completely separate from
     * other runs. However, with identity set the socket shall re-use any existing 0MQ
     * infrastructure configured by the previous run(s). Thus the application may receive
     * messages that were sent in the meantime, _message queue_ limits shall be shared with
     * previous run(s) and so on.
     * <p/>
     * Identity should be at least one byte and at most 255 bytes long. Identities starting with
     * binary zero are reserved for use by 0MQ infrastructure.
     */
    public void setIdentity(byte[] identity) {
        socket.setIdentity(identity);
    }

    /**
     * @see #setIdentity(byte[])
     */
    public byte[] getIdentity() {
        return socket.getIdentity();
    }

    /**
     * Sets the ROUTER socket behavior when an unroutable message is encountered.
     *
     * @param mandatory A value of false is the default and discards the message silently when it cannot be routed.
     *                  A value of true returns an EHOSTUNREACH error code if the message cannot be routed.
     */
    public void setRouterMandatory(boolean mandatory) {
        socket.setRouterMandatory(mandatory);
    }

    /**
     * @see #setRouterMandatory(boolean)
     */
    public boolean isRouterMandatory() {
        return socket.getRouterMandatory();
    }

    public void setBacklog(long value) {
        socket.setBacklog(value);
    }

    public long getBacklog() {
        return socket.getBacklog();
    }

    /**
     * Sets the time-to-live field in every multicast packet sent from this socket.
     * The default is 1 which means that the multicast packets don't leave the local
     * network.
     */
    public void setMulticastHops(long value) {
        socket.setMulticastHops(value);
    }

    /**
     * @see #setMulticastHops(long)
     */
    public long getMulticastHops() {
        return socket.getMulticastHops();
    }

    public void setDecoder(Class<? extends DecoderBase> cls) {
        socket.setDecoder(cls);
    }

    public void setEncoder(Class<? extends EncoderBase> cls) {
        socket.setEncoder(cls);
    }

    /**
     * The 'ZMQ_FD' option shall retrieve file descriptor associated with the 0MQ
     * socket. The descriptor can be used to integrate 0MQ socket into an existing
     * event loop. It should never be used for anything else than polling -- such as
     * reading or writing. The descriptor signals edge-triggered IN event when
     * something has happened within the 0MQ socket. It does not necessarily mean that
     * the messages can be read or written. Check ZMQ_EVENTS option to find out whether
     * the 0MQ socket is readable or writable.
     *
     * @return the underlying file descriptor.
     */
    public SelectableChannel getFileDescriptor() {
        return socket.getFD();
    }
}
