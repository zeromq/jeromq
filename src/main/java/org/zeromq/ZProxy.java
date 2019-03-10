package org.zeromq;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.ZActor.Actor;
import org.zeromq.ZAgent.SelectorCreator;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZStar.Exit;

import zmq.Msg;
import zmq.SocketBase;

/**
 * Implementation of a remotely controlled  proxy for 0MQ, using {@link ZActor}.
 * <br>
 * The goals of this implementation are to delegate the creation of sockets
 * in a background thread via a callback interface to ensure their correct use
 * and to provide ultimately to end-users the following features.
 *
 * <p>Basic features:</p>
 * <ul>
 *  <li>Remote Control
 *   <ul>
 *   <li>Start:                                 <i>if was paused, flushes the pending messages</i>
 *   <li>Pause:                                 <b><i>lets the socket queues accumulate messages according to their types</i></b>
 *   <li>Stop:                                  <i>Shutdowns the proxy, can be restarted</i>
 *   <li>Status:                                <i>Retrieves the status of the proxy</i>
 *   <li>Cold Restart:                          <i>Closes and recreates the connections</i>
 *   <li>{@link #restart(ZMsg) Hot Restart}:    <i>User-defined behavior with custom messages</i>
 *   <li>{@link #configure(ZMsg) Configure}:    <i>User-defined behavior with custom messages</i>
 *   <li>{@link #command(String, boolean)} ...: <i>Custom commands of your own</i>
 *   <li>Exit:                                  <i>Definitive shutdown of the proxy and its control</i>
 *  </ul>
 *  All the non-custom commands can be performed in asynchronous or synchronous mode.
 *  <br>
 * <li>Proxy mechanism ensured by pluggable pumps
 *    <ul>
 *      <li>with built-in low-level {@link org.zeromq.ZProxy.ZmqPump} (zmq.ZMQ): useful for performances
 *      <li>with built-in high-level  {@link org.zeromq.ZProxy.ZPump}  (ZeroMQ): useful for {@link org.zeromq.ZProxy.ZPump.Transformer message transformation}, lower performances
 *      <li>with your own-custom proxy pump implementing a {@link Pump 1-method interface}
 *    </ul>
 * </ul><p>
 * <br>
 * You can have all the above non-customizable features in about these lines of code:
 * <pre>
 * {@code
        final ZProxy.Proxy provider = new ZProxy.SimpleProxy()
        {
            public Socket create(ZContext ctx, ZProxy.Plug place, Object ... args)
            {
                assert ("TEST".equals(args[0]);
                Socket socket = null;
                if (place == ZProxy.Plug.FRONT) {
                    socket = ctx.createSocket(ZMQ.ROUTER);
                }
                if (place == ZProxy.Plug.BACK) {
                    socket = ctx.createSocket(ZMQ.DEALER);
                }
                return socket;
            }

            public void configure(Socket socket, ZProxy.Plug place, Object ... args)
            {
                assert ("TEST".equals(args[0]);
                int port = -1;
                if (place == ZProxy.Plug.FRONT) {
                    port = socket.bind("tcp://127.0.0.1:6660");
                }
                if (place == ZProxy.Plug.BACK) {
                    port = socket.bind("tcp://127.0.0.1:6661");
                }
                if (place == ZProxy.Plug.CAPTURE && socket != null) {
                    socket.bind("tcp://127.0.0.1:4263");
                }
            }
        };

        ZProxy proxy = ZProxy.newProxy("ProxyOne", provider, "ABRACADABRA", Arrays.asList("TEST"));
}
 * </pre>
 * Once created, the proxy is not started. You have to perform first a start command on it.
 * This choice was made because it is easier for a user to start it with one line of code than for the code to internally handle
 * different possible starting states (after all, someone may want the proxy started but paused at first or configured in a specific way?)
 * and because the a/sync stuff was funnier. Life is unfair ...
 * Or maybe an idea is floating in the air?
 * <br>
 * You can then use it like this:
 * <pre>
 * {@code
        final boolean async = false, sync = true;
        String status = null;
        status = proxy.status();
        status = proxy.pause(sync);
        status = proxy.start(async);
        status = proxy.restart(new ZMsg());
        status = proxy.status(async);
        status = proxy.stop(sync);
        boolean here = proxy.sign();
        ZMsg cfg = new ZMsg();
        msg.add("CONFIG-1");
        ZMsg rcvd = proxy.configure(cfg);
        proxy.exit();
        status = proxy.status(sync);
        assert (!proxy.started());
   }
 * </pre>
 *
 * A {@link #command(Command, boolean) programmatic interface} with enums is also available.
 *
 *
 */
// Proxy for 0MQ.
@SuppressWarnings("deprecation")
public class ZProxy
{
    /**
     * Possible places for sockets in the proxy.
     */
    public enum Plug
    {
        FRONT, // The position of the frontend socket.
        BACK, // The position of the backend socket.
        CAPTURE // The position of the capture socket.
    }

    // Contract for socket creation and customizable configuration in proxy threading.
    public interface Proxy
    {
        /**
         * Creates and initializes (bind, options ...) the socket for the given plug in the proxy.
         * The proxy will close them afterwards, and the context as well if not provided in the constructor.
         * There is no need to keep a reference on the created socket or the context given in parameter.
         *
         * @param ctx    the context used for initialization.
         * @param place  the position for the future created socket in the proxy.
         * @param args   the optional array of arguments that has been passed at the creation of the ZProxy.
         * @return the created socket. Possibly null only for capture.
         */
        Socket create(ZContext ctx, Plug place, Object... args);

        /**
         * Configures the given socket.
         *
         * @param socket  the socket to configure
         * @param place   the position for the socket in the proxy
         * @param args    the optional array of arguments that has been passed at the creation of the ZProxy.
         * @return true if successfully configured, otherwise false
         */
        boolean configure(Socket socket, Plug place, Object... args) throws IOException;

        /**
         * Performs a hot restart of the given socket.
         * Usually an unbind/bind but you can use whatever method you like.
         *
         * @param cfg      the custom configuration message sent by the control.
         * @param socket   the socket to hot restart
         * @param place    the position for the socket in the proxy
         * @param args     the optional array of arguments that has been passed at the creation of the ZProxy.
         * @return true to perform a cold restart instead, false to do nothing. All the results will be collected from calls for all plugs.
         * If any of them returns true, the cold restart is performed.
         */
        boolean restart(ZMsg cfg, Socket socket, Plug place, Object... args) throws IOException;

        /**
         * Configures the proxy with a custom message.
         *
         * Note: you need to send one (1) mandatory custom response message with the pipe before the end of this call.
         *
         * @param pipe      the control pipe
         * @param cfg       the custom configuration message sent by the control
         * @param frontend  the frontend socket
         * @param backend   the backend socket
         * @param capture   the optional capture socket
         * @param args      the optional array of arguments that has been passed at the creation of the ZProxy.
         * @return true to continue the proxy, false to exit
         */
        boolean configure(Socket pipe, ZMsg cfg, Socket frontend, Socket backend, Socket capture, Object... args);

        /**
         * Handles a custom command not recognized by the proxy.
         *
         * Note: you need to send the current state at the end of the call.
         *
         * @param pipe      the control pipe
         * @param cmd       the unrecognized command
         * @param frontend  the frontend socket
         * @param backend   the backend socket
         * @param capture   the optional capture socket
         * @param args      the optional array of arguments that has been passed at the creation of the ZProxy.
         *
         * @return true to continue the proxy, false to exit
         */
        boolean custom(Socket pipe, String cmd, Socket frontend, Socket backend, Socket capture, Object... args);

        // this may be useful
        public abstract static class SimpleProxy implements Proxy
        {
            @Override
            public boolean restart(ZMsg cfg, Socket socket, Plug place, Object... args) throws IOException
            {
                return true;
            }

            @Override
            public boolean configure(Socket pipe, ZMsg cfg, Socket frontend, Socket backend, Socket capture,
                                     Object... args)
            {
                return true;
            }

            @Override
            public boolean custom(Socket pipe, String cmd, Socket frontend, Socket backend, Socket capture,
                                  Object... args)
            {
                return true;
            }
        }
    }

    /**
     * Creates a new proxy in a ZeroMQ way.
     * This proxy will be less efficient than the
     * {@link #newZProxy(ZContext, String, org.zeromq.ZProxy.Proxy, String, Object...) low-level one}.
     *
     * @param ctx        the context used for the proxy.
     * Possibly null, in this case a new context will be created and automatically destroyed afterwards.
     * @param name       the name of the proxy. Possibly null.
     * @param selector   the creator of the selector used for the internal polling. Not null.
     * @param sockets    the sockets creator of the proxy. Not null.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     *
     * @return the created proxy.
     * @deprecated use {@link #newZProxy(ZContext, String, Proxy, String, Object...)} instead.
     */
    @Deprecated
    public static ZProxy newZProxy(ZContext ctx, String name, SelectorCreator selector, Proxy sockets,
                                   String motdelafin, Object... args)
    {
        return newZProxy(ctx, name, sockets, motdelafin, args);
    }

    /**
     * Creates a new proxy in a ZeroMQ way.
     * This proxy will be less efficient than the
     * {@link #newZProxy(ZContext, String, org.zeromq.ZProxy.Proxy, String, Object...) low-level one}.
     *
     * @param ctx        the context used for the proxy.
     * Possibly null, in this case a new context will be created and automatically destroyed afterwards.
     * @param name       the name of the proxy. Possibly null.
     * @param sockets    the sockets creator of the proxy. Not null.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     *
     * @return the created proxy.
     */
    public static ZProxy newZProxy(ZContext ctx, String name, Proxy sockets, String motdelafin, Object... args)
    {
        return new ZProxy(ctx, name, sockets, new ZPump(), motdelafin, args);
    }

    /**
     * Creates a new low-level proxy for better performances.
     *
     * @param ctx        the context used for the proxy.
     * Possibly null, in this case a new context will be created and automatically destroyed afterwards.
     * @param name       the name of the proxy. Possibly null.
     * @param selector   the creator of the selector used for the internal polling. Not null.
     * @param sockets    the sockets creator of the proxy. Not null.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     *
     * @return the created proxy.
     * @deprecated use {@link #newProxy(ZContext, String, Proxy, String, Object...)} instead.
     */
    // creates a new low-level proxy
    @Deprecated
    public static ZProxy newProxy(ZContext ctx, String name, SelectorCreator selector, Proxy sockets, String motdelafin,
                                  Object... args)
    {
        return newProxy(ctx, name, sockets, motdelafin, args);
    }

    /**
     * Creates a new low-level proxy for better performances.
     *
     * @param ctx        the context used for the proxy.
     * Possibly null, in this case a new context will be created and automatically destroyed afterwards.
     * @param name       the name of the proxy. Possibly null.
     * @param sockets    the sockets creator of the proxy. Not null.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     *
     * @return the created proxy.
     */
    public static ZProxy newProxy(ZContext ctx, String name, Proxy sockets, String motdelafin, Object... args)
    {
        return new ZProxy(ctx, name, sockets, new ZmqPump(), motdelafin, args);
    }

    /**
     * Starts the proxy.
     *
     * @param sync true to read the status in synchronous way, false for asynchronous mode
     * @return the read status
     */
    public String start(boolean sync)
    {
        return command(START, sync);
    }

    /**
     * Pauses the proxy.
     * A paused proxy will cease processing messages, causing
     * them to be queued up and potentially hit the high-water mark on the
     * frontend or backend socket, causing messages to be dropped, or writing
     * applications to block.
     *
     * @param sync     true to read the status in synchronous way, false for asynchronous mode
     * @return the read status
     */
    public String pause(boolean sync)
    {
        return command(PAUSE, sync);
    }

    /**
     * Stops the proxy.
     *
     * @param sync     true to read the status in synchronous way, false for asynchronous mode
     * @return the read status
     */
    public String stop(boolean sync)
    {
        return command(STOP, sync);
    }

    /**
     * Sends a command message to the proxy actor.
     * Can be useful for programmatic interfaces.
     * Does not works with commands {@link #CONFIG CONFIG} and {@link #RESTART RESTART}.
     *
     * @param command  the command to execute. Not null.
     * @param sync     true to read the status in synchronous way, false for asynchronous mode
     * @return the read status
     */
    public String command(String command, boolean sync)
    {
        Utils.checkArgument(
                            !CONFIG.equals(command),
                            "CONFIG is not useable with that API. Please use configure(ZMsg) method");
        Utils.checkArgument(
                            !RESTART.equals(command),
                            "RESTART is not useable with that API. Please use restart(ZMsg) method");
        if (STATUS.equals(command)) {
            return status(sync);
        }
        if (EXIT.equals(command)) {
            return exit();
        }
        // consume the status in the pipe
        String status = recvStatus();

        if (agent.send(command)) {
            // the pipe is refilled
            if (sync) {
                status = status(true);
            }
        }
        return status;
    }

    /**
     * Sends a command message to the proxy actor.
     * Can be useful for programmatic interfaces.
     * Does not works with commands {@link Command#CONFIG CONFIG} and {@link Command#RESTART RESTART}.
     *
     * @param command  the command to execute.
     * @param sync     true to read the status in synchronous way, false for asynchronous mode
     * @return the read state
     */
    public State command(Command command, boolean sync)
    {
        return State.valueOf(command(command.name(), sync));
    }

    /**
     * Sends a command message to the proxy actor.
     * Can be useful for programmatic interfaces.
     * Works only with commands {@link Command#CONFIG CONFIG} and {@link Command#RESTART RESTART}.
     *
     * @param command  the command to execute.
     * @param msg      the custom message to transmit.
     * @param sync     true to read the status in synchronous way, false for asynchronous mode
     * @return the response message
     */
    public ZMsg command(Command command, ZMsg msg, boolean sync)
    {
        if (command == Command.CONFIG) {
            return configure(msg);
        }
        if (command == Command.RESTART) {
            String status = restart(msg);
            msg = new ZMsg();
            msg.add(status);
            return msg;
        }
        return null;
    }

    /**
     * Configures the proxy.
     * The distant side has to send back one (1) mandatory response message.
     *
     * @param msg      the custom message sent as configuration tip
     * @return the mandatory response message of the configuration.
     */
    public ZMsg configure(ZMsg msg)
    {
        msg.addFirst(CONFIG);

        if (agent.send(msg)) {
            // consume the status in the pipe
            recvStatus();

            ZMsg reply = agent.recv();
            assert (reply != null);

            // refill the pipe with status
            agent.send(STATUS);
            return reply;
        }
        return null;
    }

    /**
     * Restarts the proxy. Stays alive.
     *
     * @param hot     null to make a cold restart (closing then re-creation of the sockets)
     *  or a configuration message to perform a configurable hot restart,
     */
    public String restart(ZMsg hot)
    {
        ZMsg msg = new ZMsg();
        msg.add(RESTART);

        final boolean cold = hot == null;
        if (cold) {
            msg.add(Boolean.toString(false));
        }
        else {
            msg.add(Boolean.toString(true));
            msg.append(hot);
        }

        String status = EXITED;
        if (agent.send(msg)) {
            status = status(false);
        }
        return status;
    }

    /**
     * Stops the proxy and exits.
     *
     * @param sync     forced to true to read the status in synchronous way.
     * @return the read status.
     * @deprecated The call is synchronous: the sync parameter is ignored,
     * as it leads to many mistakes in case of a provided ZContext.
     */
    @Deprecated
    public String exit(boolean sync)
    {
        return exit();
    }

    /**
     * Stops the proxy and exits.
     * The call is synchronous.
     *
     * @return the read status.
     *
     */
    public String exit()
    {
        agent.send(EXIT);
        exit.awaitSilent();
        agent.close();
        return EXITED;
    }

    /**
     * Inquires for the status of the proxy.
     * This call is synchronous.
     */
    public String status()
    {
        return status(true);
    }

    /**
     * Inquires for the status of the proxy.
     *
     * @param sync     true to read the status in synchronous way, false for asynchronous mode.
     * If false, you get the last cached status of the proxy
     */
    public String status(boolean sync)
    {
        if (exit.isExited()) {
            return EXITED;
        }
        try {
            String status = recvStatus();

            if (agent.send(STATUS) && sync) {
                // wait for the response to emulate sync
                status = recvStatus();
                // AND refill a status
                if (EXITED.equals(status) || !agent.send(STATUS)) {
                    return EXITED;
                }
            }
            return status;
        }
        catch (ZMQException e) {
            return EXITED;
        }
    }

    // receives the last known state of the proxy
    private String recvStatus()
    {
        if (!agent.sign()) {
            return EXITED;
        }
        // receive the status response
        final ZMsg msg = agent.recv();

        if (msg == null) {
            return EXITED;
        }

        String status = msg.popString();
        msg.destroy();
        return status;
    }

    /**
     * Binary inquiry for the status of the proxy.
     */
    public boolean isStarted()
    {
        return started();
    }

    /**
     * Binary inquiry for the status of the proxy.
     */
    public boolean started()
    {
        String status = status(true);
        return STARTED.equals(status);
    }

    // to handle commands in a more java-centric way
    public enum Command
    {
        START,
        PAUSE,
        STOP,
        RESTART,
        EXIT,
        STATUS,
        CONFIG
    }

    // commands for the control pipe
    private static final String START   = Command.START.name();
    private static final String PAUSE   = Command.PAUSE.name();
    private static final String STOP    = Command.STOP.name();
    private static final String RESTART = Command.RESTART.name();
    private static final String EXIT    = Command.EXIT.name();
    private static final String STATUS  = Command.STATUS.name();
    private static final String CONFIG  = Command.CONFIG.name();

    // to handle states in a more java-centric way
    public enum State
    {
        ALIVE,
        STARTED,
        PAUSED,
        STOPPED,
        EXITED
    }

    // state responses from the control pipe
    public static final String STARTED = State.STARTED.name();
    public static final String PAUSED  = State.PAUSED.name();
    public static final String STOPPED = State.STOPPED.name();
    public static final String EXITED  = State.EXITED.name();
    // defines the very first time where no command changing the state has been issued
    public static final String ALIVE = State.ALIVE.name();

    private static final AtomicInteger counter = new AtomicInteger();

    // the endpoint to the distant proxy actor
    private final ZAgent agent;

    // the synchronizer for exiting
    private final Exit exit;

    /**
     * Creates a new unnamed proxy.
     *
     * @param selector   the creator of the selector used for the proxy.
     * @param creator    the creator of the sockets of the proxy.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     * @deprecated use {@link #ZProxy(Proxy, String, Object...)} instead.
     */
    @Deprecated
    public ZProxy(SelectorCreator selector, Proxy creator, String motdelafin, Object... args)
    {
        this(creator, motdelafin, args);
    }

    /**
     * Creates a new unnamed proxy.
     *
     * @param creator    the creator of the sockets of the proxy.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     */
    public ZProxy(Proxy creator, String motdelafin, Object... args)
    {
        this(null, creator, null, motdelafin, args);
    }

    /**
     * Creates a new named proxy.
     *
     * @param name       the name of the proxy (used in threads naming).
     * @param selector   the creator of the selector used for the proxy.
     * @param creator    the creator of the sockets of the proxy.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     * @deprecated use {@link #ZProxy(String, Proxy, String, Object...)} instead.
     */
    @Deprecated
    public ZProxy(String name, SelectorCreator selector, Proxy creator, String motdelafin, Object... args)
    {
        this(name, creator, motdelafin, args);
    }

    /**
     * Creates a new named proxy.
     *
     * @param name       the name of the proxy (used in threads naming).
     * @param creator    the creator of the sockets of the proxy.
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     */
    public ZProxy(String name, Proxy creator, String motdelafin, Object... args)
    {
        this(name, creator, null, motdelafin, args);
    }

    /**
     * Creates a new named proxy.
     *
     * @param ctx the main context used.
     * If null, a new context will be created and closed at the stop of the operation.
     * <b>If not null, it is the responsibility of the call to close it.</b>
     * @param name       the name of the proxy (used in threads naming).
     * @param selector   the creator of the selector used for the proxy.
     * @param sockets    the creator of the sockets of the proxy.
     * @param pump       the pump used for the proxy
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     * @deprecated use {@link #ZProxy(ZContext, String, Proxy, Pump, String, Object...)} instead.
     */
    @Deprecated
    public ZProxy(ZContext ctx, String name, SelectorCreator selector, Proxy sockets, Pump pump, String motdelafin,
            Object... args)
    {
        this(ctx, name, sockets, pump, motdelafin, args);
    }

    /**
     * Creates a new named proxy. A new context will be created and closed at the stop of the operation.
     *
     * @param name       the name of the proxy (used in threads naming).
     * @param sockets    the creator of the sockets of the proxy.
     * @param pump       the pump used for the proxy
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     */
    public ZProxy(String name, Proxy sockets, Pump pump, String motdelafin, Object... args)
    {
        this(null, name, sockets, pump, motdelafin, args);
    }

    /**
     * Creates a new named proxy.
     *
     * @param ctx the main context used.
     * If null, a new context will be created and closed at the stop of the operation.
     * <b>If not null, it is the responsibility of the call to close it.</b>
     * @param name       the name of the proxy (used in threads naming).
     * @param sockets    the creator of the sockets of the proxy.
     * @param pump       the pump used for the proxy
     * @param motdelafin the final word used to mark the end of the proxy. Null to disable this mechanism.
     * @param args       an optional array of arguments that will be passed at the creation.
     */
    public ZProxy(ZContext ctx, String name, Proxy sockets, Pump pump, String motdelafin, Object... args)
    {
        super();

        // arguments parsing
        if (pump == null) {
            pump = new ZmqPump();
        }
        int count = 1;
        count += args.length;

        Object[] vars = new Object[count];

        vars[0] = sockets;
        Actor shadow = null;

        // copy the arguments and retrieve the last optional shadow given in input
        for (int index = 0; index < args.length; ++index) {
            Object arg = args[index];
            if (arg instanceof Actor) {
                shadow = (Actor) arg;
            }
            vars[index + 1] = arg;
        }

        // handle the actor
        int id = counter.incrementAndGet();
        Actor actor = new ProxyActor(name, pump, id);
        if (shadow != null) {
            actor = new ZActor.Duo(actor, shadow);
        }

        ZActor zactor = new ZActor(ctx, actor, motdelafin, vars);
        agent = zactor.agent(); // NB: the zactor is also its own agent
        exit = zactor.exit();
    }

    // defines a pump that will flow messages from one socket to another
    public interface Pump
    {
        /**
         * Transfers a message from one source to one destination, with an optional capture.
         *
         * @param src           the plug of the source socket
         * @param source        the socket where to receive the message from.
         * @param capture       the optional sockets where to send the message to. Possibly null.
         * @param dst           the plug of the destination socket
         * @param destination   the socket where to send the message to.
         *
         * @return false in case of error or interruption, true if successfully transferred the message
         */
        boolean flow(Plug src, Socket source, Socket capture, Plug dst, Socket destination);
    }

    // acts in background to proxy messages
    private static final class ProxyActor extends ZActor.SimpleActor
    {
        // the states container of the proxy
        private static final class State
        {
            @Override
            public String toString()
            {
                return "State [alive=" + alive + ", started=" + started + ", paused=" + paused + ", restart=" + restart
                        + ", hot=" + hot + "]";
            }

            // are we alive ?
            private boolean alive = false;
            // are we started ?
            private boolean started = false;
            // are we paused ?
            private boolean paused = false;

            // controls creation of a new agent if asked of a cold restart
            private boolean restart = false;
            // one-shot configuration for hot restart
            private ZMsg hot;
        }

        // the state of the proxy
        private final State state = new State();

        // used to transfer message from one socket to another
        private final Pump transport;

        // the nice name of the proxy
        private final String name;

        // the provider of the sockets
        private Proxy provider;

        // the sockets creator user arguments
        private Object[] args;

        private Socket frontend;
        private Socket backend;
        private Socket capture;

        // creates a new Proxy actor.
        public ProxyActor(String name, Pump transport, int id)
        {
            if (name == null) {
                // default basic name
                this.name = String.format("ZProxy-%sd", id);
            }
            else {
                this.name = name;
            }
            this.transport = transport;
        }

        @Override
        public String premiere(Socket pipe)
        {
            ZMsg reply = new ZMsg();
            reply.add(ALIVE);
            reply.send(pipe);

            return name;
        }

        // creates the sockets before the start of the proxy
        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            provider = (Proxy) args[0];

            this.args = new Object[args.length - 1];
            System.arraycopy(args, 1, this.args, 0, this.args.length);

            frontend = provider.create(ctx, Plug.FRONT, this.args);
            capture = provider.create(ctx, Plug.CAPTURE, this.args);
            backend = provider.create(ctx, Plug.BACK, this.args);

            assert (frontend != null);
            assert (backend != null);

            return Arrays.asList(frontend, backend);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            // init the state machine
            state.alive = true;
            state.restart = false;
        }

        // Process a control message
        @Override
        public boolean backstage(Socket pipe, ZPoller poller, int events)
        {
            assert (state.hot == null);

            String cmd = pipe.recvStr();
            // a message has been received from the API
            if (START.equals(cmd)) {
                if (start(poller)) {
                    return status().send(pipe);
                }
                // unable to start the proxy, exit
                state.restart = false;
                return false;
            }
            else if (STOP.equals(cmd)) {
                stop();
                return status().send(pipe);
            }
            else if (PAUSE.equals(cmd)) {
                pause(poller, true);
                return status().send(pipe);
            }
            else if (RESTART.equals(cmd)) {
                String val = pipe.recvStr();
                boolean hot = Boolean.parseBoolean(val);
                return restart(pipe, hot);
            }
            else if (STATUS.equals(cmd)) {
                return status().send(pipe);
            }
            else if (CONFIG.equals(cmd)) {
                ZMsg cfg = ZMsg.recvMsg(pipe);
                boolean rc = provider.configure(pipe, cfg, frontend, backend, capture, args);
                cfg.destroy();
                return rc;
            }
            else if (EXIT.equals(cmd)) {
                // stops the proxy and the agent.
                // the status will be sent at the end of the loop
                state.restart = false;
            }
            else {
                return provider.custom(pipe, cmd, frontend, backend, capture, args);
            }
            return false;
        }

        // returns the status
        private ZMsg status()
        {
            ZMsg reply = new ZMsg();
            if (!state.alive) {
                reply.add(EXITED);
            }
            else if (state.paused) {
                reply.add(PAUSED);
            }
            else if (state.started) {
                reply.add(STARTED);
            }
            else {
                reply.add(STOPPED);
            }

            return reply;
        }

        // starts the proxy sockets
        private boolean start(ZPoller poller)
        {
            boolean success = true;
            if (!state.started) {
                try {
                    success = false;
                    success |= provider.configure(frontend, Plug.FRONT, args);
                    success |= provider.configure(backend, Plug.BACK, args);
                    success |= provider.configure(capture, Plug.CAPTURE, args);
                    state.started = true;
                }
                catch (RuntimeException | IOException e) {
                    e.printStackTrace();
                    // unable to configure proxy, exit
                    state.restart = false;
                    state.started = false;
                    return false;
                }
            }
            pause(poller, false);
            return success;
        }

        // pauses the proxy sockets
        private boolean pause(ZPoller poller, boolean pause)
        {
            state.paused = pause;
            if (pause) {
                poller.unregister(frontend);
                poller.unregister(backend);
                // TODO why not a mechanism for eventually flushing the sockets during the pause?
            }
            else {
                poller.register(frontend, ZPoller.POLLIN);
                poller.register(backend, ZPoller.POLLIN);
                //  Now Wait also until there are either requests or replies to process.
            }
            return true;
        }

        private boolean stop()
        {
            // restart the actor in stopped state
            state.started = false;
            state.paused = false;
            // close connections
            state.restart = true;
            return true;
        }

        // handles the restart command in both modes
        private boolean restart(Socket pipe, boolean hot)
        {
            state.restart = true;
            if (hot) {
                assert (provider != null);
                state.hot = ZMsg.recvMsg(pipe);
                // continue with the same agent
                return true;
            }
            else {
                // stop the loop and restart a new agent
                // with the same started state
                // the next loop will refill the updated status
                return false;
            }
        }

        @Override
        public long looping(Socket pipe, ZPoller poller)
        {
            state.hot = null;
            return super.looping(pipe, poller);
        }

        // a message has been received for the proxy to process
        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            if (socket == frontend) {
                //  Process a request.
                return transport.flow(Plug.FRONT, frontend, capture, Plug.BACK, backend);
            }
            if (socket == backend) {
                //  Process a reply.
                return transport.flow(Plug.BACK, backend, capture, Plug.FRONT, frontend);
            }
            return false;
        }

        @Override
        public boolean looped(Socket pipe, ZPoller poller)
        {
            if (state.restart) {
                if (state.hot == null) {
                    // caught the cold restart
                    return false;
                }
                else {
                    // caught the hot restart
                    ZMsg cfg = state.hot;
                    state.hot = null;

                    // we perform a cold restart if the provider says so
                    boolean cold;
                    ZMsg dup = cfg.duplicate();

                    try {
                        cold = provider.restart(dup, frontend, Plug.FRONT, this.args);
                        dup.destroy();
                        dup = cfg.duplicate();
                        cold |= provider.restart(dup, backend, Plug.BACK, this.args);
                        dup.destroy();
                        dup = cfg.duplicate();
                        cold |= provider.restart(dup, capture, Plug.CAPTURE, this.args);
                        dup.destroy();
                        cfg.destroy();
                    }
                    catch (RuntimeException | IOException e) {
                        e.printStackTrace();
                        state.restart = false;
                        return false;
                    }

                    // cold restart means the loop has to stop.
                    return !cold;
                }
            }
            return true;
        }

        // called in the proxy thread when it stopped.
        @Override
        public boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller)
        {
            if (capture != null) {
                ctx.destroySocket(capture);
            }
            state.alive = false;
            if (!state.restart) {
                status().send(pipe);
            }
            return state.restart;
        }
    }

    /**
     * A pump that reads a message as a whole before transmitting it.
     * It offers a way to transform messages for capture and destination.
     */
    public static class ZPump implements Pump
    {
        private static final Identity IDENTITY = new Identity();

        // the messages transformer
        private final Transformer transformer;

        // transforms one message into another
        public interface Transformer
        {
            /**
             * Transforms a ZMsg into another ZMsg.
             * Please note that this will be used during the message transfer,
             * so lengthy operations will have a cost on performances by definition.
             * If you return back another message than the one given in input, then this one has to be destroyed by you.
             * @param msg the message to transform
             * @param src the source plug
             * @param dst the destination plug
             * @return the transformed message
             */
            ZMsg transform(ZMsg msg, Plug src, Plug dst);
        }

        private static class Identity implements Transformer
        {
            @Override
            public ZMsg transform(ZMsg msg, Plug src, Plug dst)
            {
                return msg;
            }
        }

        public ZPump()
        {
            this(null);
        }

        public ZPump(Transformer transformer)
        {
            super();
            this.transformer = transformer == null ? IDENTITY : transformer;
        }

        @Override
        public boolean flow(Plug splug, Socket source, Socket capture, Plug dplug, Socket destination)
        {
            boolean success;

            // we read the whole message
            ZMsg msg = ZMsg.recvMsg(source);

            if (msg == null) {
                return false;
            }

            if (capture != null) {
                //  Copy transformed message to capture socket if any message
                // TODO what if the transformer modifies or destroys the original message ?
                ZMsg cpt = transformer.transform(msg, splug, Plug.CAPTURE);

                //                boolean destroy = !msg.equals(cpt); // TODO ?? which one
                boolean destroy = msg != cpt;
                success = cpt.send(capture, destroy);
                if (!success) {
                    // not successful, but we can still try to send it to the destination
                }
            }

            ZMsg dst = transformer.transform(msg, splug, dplug);
            // we send the whole transformed message
            success = dst.send(destination);

            // finished
            msg.destroy();

            return success;
        }
    }

    /**
     * A specialized transport for better transmission purposes
     * that will send each packets individually instead of the whole message.
     */
    private static final class ZmqPump implements Pump
    {
        // transfers each message as a whole by sending each packet received to the capture socket
        @Override
        public boolean flow(Plug splug, Socket source, Socket capture, Plug dplug, Socket destination)
        {
            boolean rc;

            SocketBase src = source.base();
            SocketBase dst = destination.base();
            SocketBase cpt = capture == null ? null : capture.base();

            // we transfer the whole message
            while (true) {
                // we read the packet
                Msg msg = src.recv(0);

                if (msg == null) {
                    return false;
                }

                long more = src.getSocketOpt(zmq.ZMQ.ZMQ_RCVMORE);

                if (more < 0) {
                    return false;
                }

                //  Copy message to capture socket if any packet
                if (cpt != null) {
                    Msg ctrl = new Msg(msg);
                    rc = cpt.send(ctrl, more > 0 ? zmq.ZMQ.ZMQ_SNDMORE : 0);
                    if (!rc) {
                        // not successful, but we can still try to send it to the destination
                    }
                }

                // we send the packet
                rc = dst.send(msg, more > 0 ? zmq.ZMQ.ZMQ_SNDMORE : 0);

                if (!rc) {
                    return false;
                }
                if (more == 0) {
                    break;
                }
            }
            return true;
        }
    }
}
