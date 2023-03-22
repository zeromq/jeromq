package org.zeromq;

import java.nio.channels.SelectableChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMonitor.Event;

import zmq.ZError;

/**
 * A high level wrapper for an event that stores all value as Enum or java object instead of integer, and associate a
 * severity with them.
 * The events are handled using the following rules.
 * <br/>
 * <table>
 *     <caption>Events list</caption>
 *     <tr>
 *         <th>Event</th>
 *         <th>Value type</th>
 *         <th>Severity level</th>
 *     </tr>
 *     <tr>
 *         <td>CONNECTED</td>
 *         <td>{@link java.nio.channels.SelectableChannel}</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>CONNECT_DELAYED</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>CONNECT_RETRIED</td>
 *         <td>{@link java.time.Duration}</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>LISTENING</td>
 *         <td>{@link java.nio.channels.SelectableChannel}</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>BIND_FAILED</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>error</td>
 *     </tr>
 *     <tr>
 *         <td>ACCEPTED</td>
 *         <td>{@link java.nio.channels.SelectableChannel}</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>ACCEPT_FAILED</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>error</td>
 *     </tr>
 *     <tr>
 *         <td>CLOSED</td>
 *         <td>{@link java.nio.channels.SelectableChannel}</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>CLOSE_FAILED</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>error</td>
 *     </tr>
 *     <tr>
 *         <td>DISCONNECTED</td>
 *         <td>{@link java.nio.channels.SelectableChannel}</td>
 *         <td>info</td>
 *     </tr>
 *     <tr>
 *         <td>MONITOR_STOPPED</td>
 *         <td>null value</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>HANDSHAKE_FAILED_NO_DETAIL</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>error</td>
 *     </tr>
 *     <tr>
 *         <td>HANDSHAKE_SUCCEEDED</td>
 *         <td>{@link ZMQ.Error} or null if no error</td>
 *         <td>debug</td>
 *     </tr>
 *     <tr>
 *         <td>HANDSHAKE_FAILED_PROTOCOL</td>
 *         <td>{@link ZMonitor.ProtocolCode}</td>
 *         <td>error</td>
 *     </tr>
 *     <tr>
 *         <td>HANDSHAKE_FAILED_AUTH</td>
 *         <td>{@link java.lang.Integer}</td>
 *         <td>warn</td>
 *     </tr>
 *     <tr>
 *         <td>HANDSHAKE_PROTOCOL</td>
 *         <td>{@link java.lang.Integer}</td>
 *         <td>debug</td>
 *     </tr>
 * </table>
 */
public class ZEvent
{
    /**
     * An interface used to consume events in monitor
     */
    public interface ZEventConsummer extends zmq.ZMQ.EventConsummer
    {
        void consume(ZEvent ev);

        default void consume(zmq.ZMQ.Event event)
        {
            consume(new ZEvent(event, SelectableChannel.class::cast));
        }
    }

    private final Event event;
    // To keep backward compatibility, the old value field only store integer
    // The resolved value (Error, channel or other) is stored in resolvedValue field.
    private final Object value;
    private final String address;

    private ZEvent(zmq.ZMQ.Event event, Function<Object, SelectableChannel> getResolveChannel)
    {
        this.event = ZMonitor.Event.findByCode(event.event);
        this.address = event.addr;
        this.value = resolve(this.event, event.arg, getResolveChannel);
    }

    static Object resolve(Event event, Object value, Function<Object, SelectableChannel> getResolveChannel)
    {
        switch (event) {
        case HANDSHAKE_FAILED_PROTOCOL:
            return ZMonitor.ProtocolCode.findByCode((Integer) value);
        case CLOSE_FAILED:
        case ACCEPT_FAILED:
        case BIND_FAILED:
        case HANDSHAKE_FAILED_NO_DETAIL:
        case CONNECT_DELAYED:
        case HANDSHAKE_SUCCEEDED:
            return ZMQ.Error.findByCode((Integer) value);
        case HANDSHAKE_FAILED_AUTH:
        case HANDSHAKE_PROTOCOL:
            return value;
        case CONNECTED:
        case LISTENING:
        case ACCEPTED:
        case CLOSED:
        case DISCONNECTED:
            return getResolveChannel.apply(value);
        case CONNECT_RETRIED:
            return Duration.ofMillis((Integer) value);
        case MONITOR_STOPPED:
            return null;
        default:
            assert false : "Unhandled event " + event;
            return null;
        }
    }

    public Event getEvent()
    {
        return event;
    }

    /**
     * Return the value of the event as a high level java object.
     * It returns objects of type:
     * <ul>
     * <li> {@link org.zeromq.ZMonitor.ProtocolCode} for a handshake protocol error.</li>
     * <li> {@link org.zeromq.ZMQ.Error} for any other error.</li>
     * <li> {@link Duration} when associated with a delay.</li>
     * <li> null when no relevant value available.</li>
     * </ul>
     * @param <M> The expected type of the returned object
     * @return The resolved value.
     */
    @SuppressWarnings("unchecked")
    public <M> M getValue()
    {
        return (M) value;
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
     * @return true if the event was an error
     */
    public boolean isError()
    {
        switch (event) {
        case BIND_FAILED:
        case ACCEPT_FAILED:
        case CLOSE_FAILED:
        case HANDSHAKE_FAILED_NO_DETAIL:
        case HANDSHAKE_FAILED_PROTOCOL:
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
     * @return true if the event was a warning
     */
    public boolean isWarn()
    {
        return event == Event.HANDSHAKE_FAILED_AUTH;
    }

    /**
     * Used to check if the event is an information.
     * <p>
     * Generally, any event that return an authentication failure is
     * considered as a warning.
     * @return true if the event was a warning
     */
    public boolean isInformation()
    {
        return event == Event.DISCONNECTED;
    }

    /**
     * Used to check if the event is an error.
     * <p>
     * Generally, any event that define the errno is
     * considered as an error.
     * @return true if the event was an error
     */
    public boolean isDebug()
    {
        switch (event) {
        case CONNECTED:
        case CONNECT_DELAYED:
        case CONNECT_RETRIED:
        case LISTENING:
        case ACCEPTED:
        case CLOSED:
        case MONITOR_STOPPED:
        case HANDSHAKE_SUCCEEDED:
        case HANDSHAKE_PROTOCOL:
            return true;
        default:
            return false;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        else {
            ZEvent zEvent = (ZEvent) o;
            return event == zEvent.event && Objects.equals(value, zEvent.value) && address.equals(zEvent.address);
        }
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(event, value, address);
    }

    @Override
    public String toString()
    {
        return "ZEvent{" + "event=" + event + ", value=" + value + ", address='" + address + '\'' + '}';
    }

    /**
     * Receive an event from a monitor socket.
     *
     * @param socket the monitor socket
     * @param flags  the flags to apply to the read operation.
     * @return the received event or null if no message was received.
     * @throws ZMQException In case of errors with the monitor socket
     */
    public static ZEvent recv(Socket socket, int flags)
    {
        zmq.ZMQ.Event e = zmq.ZMQ.Event.read(socket.base(), flags);
        if (socket.errno() > 0 && socket.errno() != ZError.EAGAIN) {
            throw new ZMQException(socket.errno());
        }
        else if (e == null) {
            return null;
        }
        else {
            return new ZEvent(e, o -> e.getChannel(socket.getCtx()));
        }
    }

    /**
     * Receive an event from a monitor socket.
     * Does a blocking recv.
     *
     * @param socket the monitor socket
     * @return the received event or null if no message was received.
     * @throws ZMQException In case of errors with the monitor socket
     */
    public static ZEvent recv(ZMQ.Socket socket)
    {
        return recv(socket, 0);
    }
}
