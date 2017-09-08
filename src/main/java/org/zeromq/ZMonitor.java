package org.zeromq;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;

/**
 * The ZMonitor actor provides an API for obtaining socket events such as
 * connected, listen, disconnected, etc. Socket events are only available
 * for sockets connecting or bound to ipc:// and tcp:// endpoints.
 */
public class ZMonitor
{
    /**
     * High-level representation of an event.
     */
    public static final class ZEvent
    {
        /**
         * The type of the event.
         */
        public final Event  type;
        /**
         * The address of the source of the event.
         */
        public final String address;
        /**
         * String representation of the event value.
         * Nature of the value depends on the type of event.
         */
        public final String value;

        private ZEvent(ZMsg msg)
        {
            assert (msg != null);
            assert (ZEvent.class.getSimpleName().equals(msg.popString()));

            type = Event.valueOf(msg.popString());
            assert (type != null);

            address = msg.popString();
            if (msg.isEmpty()) {
                value = null;
            }
            else {
                value = msg.popString();
            }
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

        static Event find(int event)
        {
            for (Event candidate : Event.values()) {
                if ((candidate.events & event) != 0) {
                    return candidate;
                }
            }
            return null;
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
        agent.send(START);
        started = true;
        return this;
    }

    /**
     * Stops the monitoring.
     * @return this instance.
     */
    public final ZMonitor stop()
    {
        agent.send(STOP);
        started = false;
        return this;
    }

    /**
     * Closes and ends the actor.
     * When returning from that call, ZMonitor will be no more active.
     */
    public final void close()
    {
        agent.send(CLOSE);
        exit.awaitSilent();
    }

    /**
     * Sets verbosity of the monitor.
     * @param verbose
     * @return this instance.
     */
    public final ZMonitor verbose(boolean verbose)
    {
        agent.send(VERBOSE, true);
        agent.send(Boolean.toString(verbose));
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
        ZMsg msg = agent.recv(wait);
        if (msg == null) {
            return null;
        }
        return new ZEvent(msg);
    }

    /**
     * Adds event types to monitor.
     * @param events the types of events to monitor.
     * @return this instance.
     */
    public final ZMonitor addEvents(Event... events)
    {
        if (started) {
            System.out.println("Unable to add events while already started. Please stop the monitor before");
            return this;
        }
        ZMsg msg = new ZMsg();
        msg.add(ADD_EVENTS);
        for (Event evt : events) {
            msg.add(evt.name());
        }
        agent.send(msg);
        return this;
    }

    /**
     * Removes event types from monitor.
     * @param events the types of events to stop monitoring.
     * @return this instance.
     */
    public final ZMonitor removeEvents(Event... events)
    {
        if (started) {
            System.out.println("Unable to remove events while already started. Please stop the monitor before");
            return this;
        }
        ZMsg msg = new ZMsg();
        msg.add(REMOVE_EVENTS);
        for (Event evt : events) {
            msg.add(evt.name());
        }
        agent.send(msg);
        return this;
    }

    private static final String START         = "START";
    private static final String STOP          = "STOP";
    private static final String CLOSE         = "CLOSE";
    private static final String VERBOSE       = "VERBOSE";
    private static final String ADD_EVENTS    = "ADD_EVENTS";
    private static final String REMOVE_EVENTS = "REMOVE_EVENTS";

    private final ZAgent     agent;
    private final ZStar.Exit exit;

    public ZMonitor(Socket socket)
    {
        this(null, socket);

    }

    public ZMonitor(ZContext ctx, Socket socket)
    {
        assert (socket != null);
        final ZActor.Actor actor = new MonitorActor(socket);
        final ZActor zactor = new ZActor(ctx, actor, UUID.randomUUID().toString());
        agent = zactor.agent();
        exit = zactor.exit();

        // wait for the start of the actor
        agent.recv().destroy();
    }

    private static class MonitorActor extends ZActor.SimpleActor
    {
        private final Socket socket;
        private final String addr;

        private Socket sink;
        private int    events;
        private boolean verbose;

        public MonitorActor(ZMQ.Socket socket)
        {
            assert (socket != null);
            this.socket = socket;
            this.addr = String.format("inproc://zmonitor-%s", socket.hashCode());
        }

        @Override
        public String premiere(Socket pipe)
        {
            return "ZMonitor-" + socket.toString();
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            sink = ctx.createSocket(ZMQ.PAIR);
            assert (sink != null);
            return Arrays.asList(sink);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            pipe.send("OK");
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            final ZMQ.Event event = ZMQ.Event.recv(socket);
            final ZMsg msg = new ZMsg();

            final Event type = Event.find(event.getEvent());
            msg.add(ZEvent.class.getSimpleName());
            msg.add(type.name());
            msg.add(event.getAddress());

            final Object value = event.getValue();
            if (value != null) {
                msg.add(value.toString());
            }
            return msg.send(pipe, true);
        }

        @Override
        public boolean backstage(ZMQ.Socket pipe, ZPoller poller, int events)
        {
            final String command = pipe.recvStr();
            switch (command) {
            case VERBOSE:
                verbose = Boolean.parseBoolean(pipe.recvStr());
                return true;
            case ADD_EVENTS:
                return addEvents(pipe);
            case REMOVE_EVENTS:
                return removeEvents(pipe);
            case START:
                return startMonitor(poller);
            case STOP:
                return stopMonitor(poller);
            case CLOSE:
                stopMonitor(poller);
                if (verbose) {
                    System.out.printf("I: Closing monitor %s%n", socket);
                }
                return false;
            default:
                // unknown command
                System.out.printf("E: Closing monitor %s : Unknown command %s%n", socket, command);
                return true;
            }
        }

        private boolean addEvents(Socket pipe)
        {
            final ZMsg msg = ZMsg.recvMsg(pipe);
            for (ZFrame frame : msg) {
                final String evt = frame.getString(ZMQ.CHARSET);
                final Event event = Event.valueOf(evt);
                if (verbose) {
                    System.out.printf("I: Adding" + " event %s%n", event);
                }
                events |= event.events;
            }
            return true;
        }

        private boolean removeEvents(Socket pipe)
        {
            final ZMsg msg = ZMsg.recvMsg(pipe);
            for (ZFrame frame : msg) {
                final String evt = frame.getString(ZMQ.CHARSET);
                final Event event = Event.valueOf(evt);
                if (verbose) {
                    System.out.printf("I: Removing" + " event %s%n", event);
                }
                events &= ~event.events;
            }
            return true;
        }

        private boolean startMonitor(ZPoller poller)
        {
            boolean rc = socket.monitor(addr, events);
            String err = "Unable to monitor socket " + socket;
            if (rc) {
                err = "Unable to connect monitoring socket " + sink;
                rc = sink.connect(addr);
            }
            if (rc) {
                err = "Unable to poll monitoring socket " + sink;
                rc = poller.register(sink, ZPoller.IN);
            }
            log("tart", rc, err);
            return rc;
        }

        private boolean stopMonitor(ZPoller poller)
        {
            boolean rc = poller.unregister(sink);
            String err = "Unable to unregister monitoring socket " + sink;
            if (rc) {
                err = "Unable to stop monitor socket " + socket;
                rc = socket.monitor(null, events);
            }
            if (rc) {
                err = "Unable to disconnect monitoring socket " + sink;
                rc = sink.disconnect(addr);
            }
            log("top", rc, err);
            return rc;
        }

        private void log(String action, boolean rc, String err)
        {
            if (verbose) {
                if (rc) {
                    System.out.printf("I: S%s monitor for events %s on %s%n", action, events, socket);
                }
                else {
                    System.out
                            .printf("E: Unable to s%s monitor for events %s (%s) on %s%n", action, events, err, socket);
                }
            }
        }
    }
}
