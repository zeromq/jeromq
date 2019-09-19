package org.zeromq;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;

import zmq.util.Objects;

/**
 * The ZMonitor actor provides an API for obtaining socket events such as
 * connected, listen, disconnected, etc. Socket events are only available
 * for sockets connecting or bound to ipc:// and tcp:// endpoints.
 */
public class ZMonitor implements Closeable
{
    /**
     * High-level representation of an event.
     */
    public static final class ZEvent
    {
        /**
         * The type of the event. (never null)
         */
        public final Event type;

        /**
         * The numerical code of the event. (useful in case of unrecognized event, in which case type is ALL)
         */
        public final int code;

        /**
         * The address of the source of the event.
         */
        public final String address;

        /**
         * String representation of the event value.
         * Nature of the value depends on the type of event. May be null
         */
        public final String value;

        private ZEvent(ZMsg msg)
        {
            assert (msg != null);
            assert (msg.size() == 3 || msg.size() == 4) : msg.size();

            type = Event.valueOf(msg.popString());
            code = Integer.valueOf(msg.popString());
            address = msg.popString();

            if (msg.isEmpty()) {
                value = null;
            }
            else {
                value = msg.popString();
            }
        }

        @Override
        public String toString()
        {
            return "ZEvent [type=" + type + ", code=" + code + ", address=" + address + ", value=" + value + "]";
        }
    }

    /**
     * Enumerates types of monitoring events.
     */
    public enum Event
    {
        CONNECTED(ZMQ.EVENT_CONNECTED),
        CONNECT_DELAYED(ZMQ.EVENT_CONNECT_DELAYED),
        CONNECT_RETRIED(ZMQ.EVENT_CONNECT_RETRIED),
        LISTENING(ZMQ.EVENT_LISTENING),
        BIND_FAILED(ZMQ.EVENT_BIND_FAILED),
        ACCEPTED(ZMQ.EVENT_ACCEPTED),
        ACCEPT_FAILED(ZMQ.EVENT_ACCEPT_FAILED),
        CLOSED(ZMQ.EVENT_CLOSED),
        CLOSE_FAILED(ZMQ.EVENT_CLOSE_FAILED),
        DISCONNECTED(ZMQ.EVENT_DISCONNECTED),
        MONITOR_STOPPED(ZMQ.EVENT_MONITOR_STOPPED),
        HANDSHAKE_PROTOCOL(ZMQ.EVENT_HANDSHAKE_PROTOCOL),
        ALL(ZMQ.EVENT_ALL);

        private final int events;

        Event(int events)
        {
            this.events = events;
        }

        private static Event find(int event)
        {
            for (Event candidate : Event.values()) {
                if ((candidate.events & event) != 0) {
                    return candidate;
                }
            }
            // never arrives here, but anyway...
            return ALL;
        }
    }

    private boolean started;

    /**
     * Starts the monitoring.
     * Event types have to be added before the start, or they will take no effect.
     * @return this instance.
     */
    public final ZMonitor start()
    {
        if (started) {
            System.out.println("ZMonitor: Unable to start while already started.");
            return this;
        }
        agent.send(START);
        agent.recv();
        started = true;
        return this;
    }

    /**
     * Stops the monitoring and closes the actor.
     * When returning from that call, ZMonitor will be no more active.
     */
    @Override
    public final void close() throws IOException
    {
        destroy();
    }

    /**
     * Stops the monitoring and closes the actor.
     * When returning from that call, ZMonitor will be no more active.
     */
    public final void destroy()
    {
        agent.send(CLOSE);
        exit.awaitSilent();
        agent.close();
    }

    /**
     * Sets verbosity of the monitor.
     * @param verbose true for monitor to be verbose, otherwise false.
     * @return this instance.
     */
    public final ZMonitor verbose(boolean verbose)
    {
        if (started) {
            System.out.println("ZMonitor: Unable to change verbosity while already started.");
            return this;
        }
        agent.send(VERBOSE, true);
        agent.send(Boolean.toString(verbose));
        agent.recv();
        return this;
    }

    /**
     * Adds event types to monitor.
     * @param events the types of events to monitor.
     * @return this instance.
     */
    public final ZMonitor add(Event... events)
    {
        if (started) {
            System.out.println("ZMonitor: Unable to add events while already started.");
            return this;
        }
        ZMsg msg = new ZMsg();
        msg.add(ADD_EVENTS);
        for (Event evt : events) {
            msg.add(evt.name());
        }
        agent.send(msg);
        agent.recv();
        return this;
    }

    /**
     * Removes event types from monitor.
     * @param events the types of events to stop monitoring.
     * @return this instance.
     */
    public final ZMonitor remove(Event... events)
    {
        if (started) {
            System.out.println("ZMonitor: Unable to remove events while already started.");
            return this;
        }
        ZMsg msg = new ZMsg();
        msg.add(REMOVE_EVENTS);
        for (Event evt : events) {
            msg.add(evt.name());
        }
        agent.send(msg);
        agent.recv();
        return this;
    }

    /**
     * Gets the next event, blocking for it until available.
     * @return the next monitored event or null if closed.
     */
    public final ZEvent nextEvent()
    {
        return nextEvent(true);
    }

    /**
     * Gets the next event, blocking for it until available if requested.
     * @param wait true to block until next event is available, false to immediately return with null if there is no event.
     * @return the next event or null if there is currently none.
     */
    public final ZEvent nextEvent(boolean wait)
    {
        if (!started) {
            System.out.println("ZMonitor: Start before getting events.");
            return null;
        }
        ZMsg msg = agent.recv(wait);
        if (msg == null) {
            return null;
        }
        return new ZEvent(msg);
    }

    /**
     * Gets the next event, blocking for it until available if requested.
     * @param timeout the time in milliseconds to wait for a message before returning null, -1 to block.
     * @return the next event or null if there is currently none after the specified timeout.
     */
    public final ZEvent nextEvent(int timeout)
    {
        if (!started) {
            System.out.println("ZMonitor: Start before getting events.");
            return null;
        }
        ZMsg msg = agent.recv(timeout);
        if (msg == null) {
            return null;
        }
        return new ZEvent(msg);
    }

    private static final String START         = "START";
    private static final String CLOSE         = "CLOSE";
    private static final String VERBOSE       = "VERBOSE";
    private static final String ADD_EVENTS    = "ADD_EVENTS";
    private static final String REMOVE_EVENTS = "REMOVE_EVENTS";

    private final ZAgent     agent;
    private final ZStar.Exit exit;

    /**
     * Creates a monitoring actor for the given socket.
     * @param ctx the context relative to this actor. Not null.
     * @param socket the socket to monitor for events. Not null.
     */
    public ZMonitor(ZContext ctx, Socket socket)
    {
        Objects.requireNonNull(ctx, "ZMonitor works only with a supplied context");
        Objects.requireNonNull(socket, "Socket has to be supplied");
        final MonitorActor actor = new MonitorActor(socket);
        final ZActor zactor = new ZActor(ctx, actor, UUID.randomUUID().toString());

        agent = zactor.agent();
        exit = zactor.exit();

        // wait for the start of the actor
        agent.recv().destroy();
    }

    private static class MonitorActor extends ZActor.SimpleActor
    {
        private static final String ERROR = "ERROR";
        private static final String OK    = "OK";

        private final Socket monitored; // the monitored socket
        private final String address;   // the address where events will be collected

        private Socket  monitor; // the monitoring socket
        private int     events;  // the events to monitor
        private boolean verbose;

        public MonitorActor(ZMQ.Socket socket)
        {
            assert (socket != null);
            this.monitored = socket;
            this.address = String.format("inproc://zmonitor-%s-%s", socket.hashCode(), UUID.randomUUID().toString());
        }

        @Override
        public String premiere(Socket pipe)
        {
            return "ZMonitor-" + monitored.toString();
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            monitor = ctx.createSocket(SocketType.PAIR);
            assert (monitor != null);

            return Collections.singletonList(monitor);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            pipe.send("STARTED");
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int evts)
        {
            final ZMQ.Event event = ZMQ.Event.recv(socket);
            assert (event != null);
            final int code = event.getEvent();
            final String address = event.getAddress();
            assert (address != null);
            final Event type = Event.find(code);
            assert (type != null);

            final ZMsg msg = new ZMsg();

            msg.add(type.name());
            msg.add(Integer.toString(code));
            msg.add(address);

            final Object value = event.getValue();
            if (value != null) {
                msg.add(value.toString());
            }
            return msg.send(pipe, true);
        }

        @Override
        public boolean backstage(ZMQ.Socket pipe, ZPoller poller, int evts)
        {
            final String command = pipe.recvStr();
            if (command == null) {
                System.out.printf("ZMonitor: Closing monitor %s : No command%n", monitored);
                return false;
            }
            switch (command) {
            case VERBOSE:
                verbose = Boolean.parseBoolean(pipe.recvStr());
                return pipe.send(OK);
            case ADD_EVENTS:
                return addEvents(pipe);
            case REMOVE_EVENTS:
                return removeEvents(pipe);
            case START:
                return start(poller, pipe);
            case CLOSE:
                return close(poller, pipe);
            default:
                System.out.printf("ZMonitor: Closing monitor %s : Unknown command %s%n", monitored, command);
                pipe.send(ERROR);
                return false;
            }
        }

        private boolean addEvents(Socket pipe)
        {
            final ZMsg msg = ZMsg.recvMsg(pipe);
            if (msg == null) {
                return false; // interrupted
            }
            for (ZFrame frame : msg) {
                final String evt = frame.getString(ZMQ.CHARSET);
                final Event event = Event.valueOf(evt);
                if (verbose) {
                    System.out.printf("ZMonitor: Adding" + " event %s%n", event);
                }
                events |= event.events;
            }
            return pipe.send(OK);
        }

        private boolean removeEvents(Socket pipe)
        {
            final ZMsg msg = ZMsg.recvMsg(pipe);
            if (msg == null) {
                return false; // interrupted
            }
            for (ZFrame frame : msg) {
                final String evt = frame.getString(ZMQ.CHARSET);
                final Event event = Event.valueOf(evt);
                if (verbose) {
                    System.out.printf("ZMonitor: Removing" + " event %s%n", event);
                }
                events &= ~event.events;
            }
            return pipe.send(OK);
        }

        private boolean start(ZPoller poller, Socket pipe)
        {
            boolean rc = true;
            String err = "";
            if (rc) {
                rc = monitored.monitor(address, events);
                err = "Unable to monitor socket " + monitored;
            }
            if (rc) {
                err = "Unable to connect monitoring socket " + monitor;
                rc = monitor.connect(address);
            }
            if (rc) {
                err = "Unable to poll monitoring socket " + monitor;
                rc = poller.register(monitor, ZPoller.IN);
            }
            log("tart", rc, err);
            if (rc) {
                return pipe.send(OK);
            }
            pipe.send(ERROR);
            return false;
        }

        private boolean close(ZPoller poller, Socket pipe)
        {
            boolean rc = poller.unregister(monitor);
            String err = "Unable to unregister monitoring socket " + monitor;
            if (rc) {
                err = "Unable to stop monitor socket " + monitored;
                rc = monitored.monitor(null, events);
            }
            log("top", rc, err);

            if (verbose) {
                System.out.printf("ZMonitor: Closing monitor %s%n", monitored);
            }
            pipe.send(rc ? OK : ERROR);
            return false;
        }

        private void log(String action, boolean rc, String err)
        {
            if (verbose) {
                if (rc) {
                    System.out.printf("ZMonitor: S%s monitor for events %s on %s%n", action, events, monitored);
                }
                else {
                    System.out.printf(
                                      "ZMonitor: Unable to s%s monitor for events %s (%s) on %s%n",
                                      action,
                                      events,
                                      err,
                                      monitored);
                }
            }
        }
    }
}
