package org.zeromq;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

/**
 * ZeroMQ sockets present an abstraction of an asynchronous message queue, with the exact queuing
 * semantics depending on the socket type in use. Where conventional sockets transfer streams of
 * bytes or discrete datagrams, ZeroMQ sockets transfer discrete messages.
 * <p>
 * ZeroMQ sockets being asynchronous means that the timings of the physical connection setup and
 * tear down, reconnect and effective delivery are transparent to the user and organized by ZeroMQ
 * itself. Further, messages may be queued in the event that a peer is unavailable to receive them.
 * </p>
 */
public class ZSocket implements AutoCloseable
{
    public static final Charset UTF8 = Charset.forName("UTF-8");
    private final SocketBase    socketBase;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Create a ZeroMQ socket
     *
     * @param socketType ZeroMQ socket type
     */
    public ZSocket(final int socketType)
    {
        socketBase = ManagedContext.getInstance().createSocket(socketType);
    }

    /**
     * Retrieve the socket type for the current 'socket'. The socket type is specified at socket
     * creation time and cannot be modified afterwards.
     *
     * @return the socket's type.
     */
    public int getType()
    {
        return (int) getOption(ZMQ.ZMQ_TYPE);
    }

    /**
     * Creates an endpoint for accepting connections and binds to it.
     * <p>
     * The endpoint argument is a string consisting of two parts as follows: transport ://address. The
     * transport part specifies the underlying transport protocol to use. The meaning of the address
     * part is specific to the underlying transport protocol selected.
     * </p>
     *
     * @param endpoint the endpoint to bind to
     * @return returns true if bind to the endpoint was successful
     */
    public boolean bind(final String endpoint)
    {
        final boolean result = socketBase.bind(endpoint);
        mayRaise();
        return result;
    }

    /**
     * Stop accepting connections on a socket.
     * <p>
     * Shall unbind from the endpoint specified by the endpoint argument.
     * </p>
     *
     * @param endpoint the endpoint to unbind from
     * @return returns true if unbind to the endpoint was successful
     */
    public boolean unbind(final String endpoint)
    {
        final boolean result = socketBase.bind(endpoint);
        mayRaise();
        return result;
    }

    /**
     * Connects the socket to an endpoint and then accepts incoming connections on that endpoint.
     * <p>
     * The endpoint is a string consisting of a transport :// followed by an address. The transport
     * specifies the underlying protocol to use. The address specifies the transport-specific address
     * to connect to.
     * </p>
     *
     * @param endpoint the endpoint to connect to
     * @return returns true if connecting to the endpoint was successful
     */
    public boolean connect(final String endpoint)
    {
        final boolean result = socketBase.connect(endpoint);
        mayRaise();
        return result;
    }

    /**
     * Disconnecting a socket from an endpoint.
     *
     * @param endpoint the endpoint to disconnect from
     * @return returns true if disconnecting to endpoint was successful
     */
    public boolean disconnect(final String endpoint)
    {
        final boolean result = socketBase.termEndpoint(endpoint);
        mayRaise();
        return result;
    }

    /**
     * Returns a boolean value indicating if the multipart message currently being read from the
     * {@code Socket} and has more message parts to follow. If there are no message parts to follow or
     * if the message currently being read is not a multipart message a value of false shall be
     * returned. Otherwise, a value of true shall be returned.
     *
     * @return true if there are more messages to receive.
     */
    public final boolean hasReceiveMore()
    {
        return (int) getOption(ZMQ.ZMQ_RCVMORE) == 1;
    }

    public void subscribe(byte[] topic)
    {
        setOption(ZMQ.ZMQ_SUBSCRIBE, topic);
    }

    public void subscribe(String topic)
    {
        setOption(ZMQ.ZMQ_SUBSCRIBE, topic.getBytes(UTF8));
    }

    public void unsubscribe(byte[] topic)
    {
        setOption(ZMQ.ZMQ_UNSUBSCRIBE, topic);
    }

    public void unsubscribe(String topic)
    {
        setOption(ZMQ.ZMQ_UNSUBSCRIBE, topic.getBytes(UTF8));
    }

    public int send(byte[] b)
    {
        return send(b, 0);
    }

    public int send(byte[] b, int flags)
    {
        final Msg msg = new Msg(b);
        if (socketBase.send(msg, flags)) {
            return msg.size();
        }
        mayRaise();
        return -1;
    }

    /**
     * Send a frame
     *
     * @param frame
     * @param flags
     * @return return true if successful
     */
    public boolean sendFrame(ZFrame frame, int flags)
    {
        final byte[] data = frame.getData();
        final Msg msg = new Msg(data);
        if (socketBase.send(msg, flags)) {
            return true;
        }
        mayRaise();
        return false;
    }

    public boolean sendMessage(ZMsg message)
    {
        ZFrame frame = message.pop();
        boolean rc = false;
        while (frame != null) {
            rc = sendFrame(frame, !message.isEmpty() ? ZMQ.ZMQ_MORE : 0);
            if (!rc) {
                break;
            }
            frame = message.pop();
        }
        return rc;
    }

    public int sendStringUtf8(String str)
    {
        return sendStringUtf8(str, 0);
    }

    public int sendStringUtf8(String str, int flags)
    {
        final byte[] b = str.getBytes(UTF8);
        return send(b, flags);
    }

    public byte[] receive()
    {
        return receive(0);
    }

    public byte[] receive(int flags)
    {
        final Msg msg = socketBase.recv(flags);
        if (msg == null) {
            return null;
        }
        return msg.data();
    }

    public String receiveStringUtf8()
    {
        return receiveStringUtf8(0);
    }

    public String receiveStringUtf8(int flags)
    {
        final byte[] b = receive(flags);
        return new String(b, UTF8);
    }

    private void mayRaise()
    {
        final int errno = socketBase.errno();
        if (errno != 0 && errno != ZError.EAGAIN) {
            throw new ZMQException(errno);
        }
    }

    private void setOption(int option, Object value)
    {
        try {
            socketBase.setSocketOpt(option, value);
        }
        catch (ZMQException e) {
        }
    }

    private Object getOption(int option)
    {
        return socketBase.getSocketOptx(option);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        if (isClosed.compareAndSet(false, true)) {
            ManagedContext.getInstance().destroy(socketBase);
        }
    }
}
