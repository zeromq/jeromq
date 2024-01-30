package org.zeromq;

import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller.EventsHandler;

import zmq.util.Objects;

/**
 * <p>First implementation of a background actor remotely controlled for ØMQ.</p>
 *
 * <p>This implementation is based on the {@link ZStar} one (TODO via inheritance now but this is not totally stamped and the use of the ZAgent would probably limit the collateral damages if changed)
 * so you should be first familiar with the metaphor there.</p>
 *
 * <p>To extensively sum up:
 * <br>
 * A side or endpoint designates the same thing: the thread where lives one of the two parts of the Actor system.
 * </p>
 * An actor has 2 sides (with a trial to use theater terms for fun (: and hopefully clarity :)
 * <ul>
 * <li>the Corbeille side, or control side
 * <br>
 * This is where one can {@link #send(ZMsg) send} and {@link #recv() receive} control messages to the underneath actor via the ZActor and/or its agent.
 * <br>
 * The ZActor lives on this side and is a way to safely communicate with the distant provided Actor
 * <br>
 * Note: Corbeille is a french word designing the most privileged seats in the theater,
 * 1st floor, just above the orchestra (Wikipedia...).
 * It was the place where the King was standing during the shows, with the best seat of course!
 * <p>
 * Fast users who would want to communicate with the distant {@link Actor} can use
 * {@link #send(ZMsg)} or {@link #recv()} from the ZActor as the ZActor is itself an agent!</li>
 *
 * <li>the Plateau side, or background side where all the performances are made by the provided actor.
 * <br>
 * The provided {@link Actor} is living on the Plateau.
 * <br>
 * The Plateau is made of the Stage where {@link org.zeromq.ZActor.Actor#stage(org.zeromq.ZMQ.Socket, org.zeromq.ZMQ.Socket, org.zeromq.ZPoller, int) the effective processing occurs} and of the Backstage
 * where the provided actor can {@link org.zeromq.ZActor.Actor#backstage(org.zeromq.ZMQ.Socket, org.zeromq.ZPoller, int) send and receive} control messages to and from the ZActor.
 * <br>
 * From this side, the work is done via callbacks using the template-method pattern, applied with interfaces (?)
 * </li>
 * </ul>
 * <p>The main purpose (or at least its intent) of this class is to clarify the contracts, roles, responsibilities and action levers related to the Plateau.
 * <br>
 * The provided Actor will not be alone to do the processing of each loop.
 * <br>
 * It will be helped by a double responsible for passing the appropriate structures at the right moment.
 * As a developer point of view, this double helps also to limit the complexity of the process.
 * However Double is an uncommon one, capturing all the lights while letting the Actor taking care of all the invisible work in the shadows.
 * And this is one of the points where we begin to reach the limits of the metaphor...
 * <br>
 * The ZActor takes care of the establishment of the background processing, calling the provided Actor
 * at appropriate times via its Double. It can also manage the {@link #sign() exited} state on the control side,
 * if using the provided {@link #send(ZMsg)} or {@link #recv()} methods.
 * <br>
 * It also takes care of the automatic closing of sockets and context if it had to create one.
 * <br>
 * An {@link Actor actor} is basically a contract interface that anyone who uses this ZActor SHALL comply to.<br>
 * </p>
 *
 * <p>TODO This interface is still a little bit tough, as instead of the 5+2 Star callbacks, here are 10!
 * But they allow you to have a hand on the key points of the looping, restart a new actor if needed, ...
 * Anyway, adapters like a {@link SimpleActor simple one} or a {@link Duo duo} are present to help you on that, reducing the amount of
 * methods to write.</p>
 *
 * <p>PS: Je sais qu'il y a une différence entre acteur et comédien :)</p>
 * <p>PPS: I know nothing about theater!</p>
 *
 * <p>Example of code for a minimalistic actor with no socket handling other than the control pipe:</p>
 *
 * <pre>
 * {@code
        Actor acting = new ZActor.SimpleActor()
        {
            public List<Socket> createSockets(ZContext ctx, Object ... args)
            {
                assert ("TEST".equals(args[0]));
                return Arrays.asList(ctx.createSocket(ZMQ.PUB));
            }

            public boolean backstage(Socket pipe, ZPoller poller, int events)
            {
                String cmd = pipe.recvStr();
                if ("HELLO".equals(cmd)) {
                    pipe.send("WORLD");
                    // end of the actor
                    return false;
                }
                return true;
            }
        };
        ZActor actor = new ZActor(acting, "LOCK", Arrays.asList("TEST").toArray());
        Socket pipe = actor.pipe();
        boolean rc = pipe.send("HELLO");
        assert (rc);
        ZMsg msg = actor.recv();
        String world = msg.popString();
        assert ("WORLD".equals(world));
        msg = actor.recv();
        assert (msg == null);
        rc = actor.sign();
        assert (!rc);
        rc = actor.send("whatever");
        assert (!rc);
        // don't try to use the pipe
}</pre>
 */
// remote controlled background message processing API for 0MQ.
public class ZActor extends ZStar
// once on stage, any actor IS a star
{
    /**
     * Defines the contract for the acting instance.
     *
     */
    // contract interface for acting on the distant stage
    // TODO what can be done to reduce the amount of methods ? Split interface?
    public interface Actor
    {
        /**
         * This is the grand premiere!
         * Called before the creation of the first double and the sockets
         * 2nd in the order call of the global acting. It return the name
         * that will be used for the thread. If it's null, a default one
         * will be picked by ZActor.
         *
         * @param pipe   the backstage control pipe
         * @return the name of the upcoming performance.
         */
        String premiere(Socket pipe);

        /**
         * Creates and initializes sockets for the double.
         * This is done at each creation of a new double.
         * 3rd in the order call of the global acting.
         * 1st in the order call of the new double.
         *
         * @param ctx    the context used to create sockets
         * @param args   the arguments passed as parameters of the ZActor
         * @return a list of created sockets that will be managed by the double. Not null.
         */
        List<Socket> createSockets(ZContext ctx, Object... args);

        /**
         * Called when the double is started, before the first loop.
         * 4th in the order call of the global acting.
         * 2nd in the order call of the new double.
         *
         * @param pipe     the backstage control pipe
         * @param sockets  the managed sockets that were created in the previous step
         * @param poller   the poller where to eventually register the sockets for events
         */
        void start(Socket pipe, List<Socket> sockets, ZPoller poller);

        /**
         * Called every time just before a loop starts.
         * 5th in the order call of the global acting.
         * 3nd in the order call of the new double.
         * 1st in the order call of the new loop.
         *
         * @param pipe   the backstage control pipe
         * @param poller the poller of the double
         * @return the timeout of the coming loop. <b>-1 to block, 0 to not wait, &gt; 0 to wait</b> till max the returned duration in milliseconds
         */
        long looping(Socket pipe, ZPoller poller);

        /**
         * Called when the actor received a control message from its pipe during a loop.
         * 2nd in the order call of the new loop.
         *
         * @param pipe    the backstage control pipe receiving the message
         * @param poller  the poller of the double.
         * @param events  the events source of the call
         * @return true in case of success, <b>false to stop the actor</b>.
         */
        boolean backstage(Socket pipe, ZPoller poller, int events);

        /**
         * The actor received a message from the created sockets during a loop.
         * 2nd ex-aequo in the order call of the new loop.
         *
         * @param socket  the socket receiving the message
         * @param pipe    the backstage control pipe
         * @param poller  the poller of the double.
         * @param events  the events source of the call
         * @return true in case of success, <b>false to stop the actor</b>.
         */
        boolean stage(Socket socket, Socket pipe, ZPoller poller, int events);

        /**
         * Called at the end of each loop.
         * 3rd in the order call of the new loop.
         *
         * @param pipe   the backstage control pipe
         * @param poller the poller of the double.
         * @return true to continue with the current doppelganger, <b>false to stop it</b>.
         */
        boolean looped(Socket pipe, ZPoller poller);

        /**
         * Called when a created socket has been closed.
         * The process of destroying the double has already started.
         *
         * @param socket   the closed socked.
         */
        void closed(Socket socket);

        /**
         * Called when the current double has been destroyed.
         * Last in the order call of the double.
         *
         * @param ctx    the context.
         * @param pipe   the backstage control pipe.
         * @param poller the poller of the double.
         * @return <b>true to restart a new double</b>, false to stop the acting process
         */
        boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller);

        /**
         * Called when the stage is finished.
         * This is the last call to the actor.
         * Last in the order call of the global acting.
         *
         * @param pipe   the backstage control pipe
         * @return true to spread the word of the actor's leaving
         */
        boolean finished(Socket pipe);
    }

    /**
     * Simple adapter for an actor, with no sockets, blocking calls and immediate termination.
     */
    // simple contract implementation for acting on the stage
    public static class SimpleActor implements Actor
    {
        @Override
        public String premiere(final Socket pipe)
        {
            return null;
        }

        @Override
        public List<Socket> createSockets(final ZContext ctx, final Object... args)
        {
            return Collections.emptyList();
        }

        @Override
        public void start(final Socket pipe, final List<Socket> sockets, final ZPoller poller)
        {
            // do nothing
        }

        @Override
        public long looping(Socket pipe, ZPoller poller)
        {
            // blocking loop
            return -1;
        }

        @Override
        public boolean backstage(final Socket pipe, final ZPoller poller, final int events)
        {
            // stop looping
            return false;
        }

        @Override
        public boolean stage(final Socket socket, final Socket pipe, final ZPoller poller, int events)
        {
            // stop looping
            return false;
        }

        @Override
        public boolean looped(final Socket pipe, final ZPoller poller)
        {
            // continue with the same double
            return true;
        }

        @Override
        public void closed(final Socket socket)
        {
            // do nothing
        }

        @Override
        public boolean destroyed(final ZContext ctx, final Socket pipe, final ZPoller poller)
        {
            // no restart
            return false;
        }

        @Override
        public boolean finished(final Socket pipe)
        {
            // mot de la fin if not null
            return true;
        }
    }

    /**
     * Another actor will be called just before the main one,
     * without participating to the decisions.
     * This is interesting as a shadowed observer of the actor's behavior.
     */
    // contract implementation for a duo actor on the stage
    public static class Duo implements Actor
    {
        // the actor that will play an active role on the stage
        private final Actor main;
        // the actor that will play a passive role on the stage
        private final Actor shadow;

        public Duo(final Actor main, final Actor shadow)
        {
            super();
            Objects.requireNonNull(main, "Actor shall be set to a non-null value");
            Objects.requireNonNull(shadow, "Shadow Actor shall be set to a non-null value");
            this.main = main;
            this.shadow = shadow;
        }

        @Override
        public String premiere(final Socket pipe)
        {
            shadow.premiere(pipe);
            return main.premiere(pipe);
        }

        @Override
        public List<Socket> createSockets(final ZContext ctx, final Object... args)
        {
            shadow.createSockets(ctx, args);
            return main.createSockets(ctx, args);
        }

        @Override
        public void start(final Socket pipe, final List<Socket> sockets, final ZPoller poller)
        {
            shadow.start(pipe, sockets, poller);
            main.start(pipe, sockets, poller);
        }

        @Override
        public long looping(Socket pipe, ZPoller poller)
        {
            shadow.looping(pipe, poller);
            return main.looping(pipe, poller);
        }

        @Override
        public boolean backstage(final Socket pipe, final ZPoller poller, final int events)
        {
            shadow.backstage(pipe, poller, events);
            return main.backstage(pipe, poller, events);
        }

        @Override
        public boolean stage(final Socket socket, final Socket pipe, final ZPoller poller, final int events)
        {
            shadow.stage(socket, pipe, poller, events);
            return main.stage(socket, pipe, poller, events);
        }

        @Override
        public boolean looped(final Socket pipe, final ZPoller poller)
        {
            shadow.looped(pipe, poller);
            return main.looped(pipe, poller);
        }

        @Override
        public void closed(final Socket socket)
        {
            shadow.closed(socket);
            main.closed(socket);
        }

        @Override
        public boolean destroyed(final ZContext ctx, final Socket pipe, final ZPoller poller)
        {
            shadow.destroyed(ctx, pipe, poller);
            return main.destroyed(ctx, pipe, poller);
        }

        @Override
        public boolean finished(final Socket pipe)
        {
            shadow.finished(pipe);
            return main.finished(pipe);
        }
    }

    /**
     * Creates a new ZActor. A new context will be created and closed at the stop of the operation.
     *
     * @param actor
     *            the actor handling messages from either stage and backstage
     * @param motdelafin
     *            the final word used to mark the end of the actor. Null to disable this mechanism.
     * @param args
     *            the optional arguments that will be passed to the distant actor
     */
    public ZActor(final Actor actor, final String motdelafin, final Object... args)
    {
        super(new ActorFortune(actor), motdelafin, args);
    }

    /**
     * Creates a new ZActor.
     *
     * @param selector
     *            the creator of the selector used on the Plateau.
     * @param actor
     *            the actor handling messages from either stage and backstage
     * @param motdelafin
     *            the final word used to mark the end of the actor. Null to disable this mechanism.
     * @param args
     *            the optional arguments that will be passed to the distant actor
     * @deprecated use {@link ZActor#ZActor(Actor, String, Object...)}
     */
    @Deprecated
    public ZActor(final SelectorCreator selector, final Actor actor, final String motdelafin, final Object... args)
    {
        this(actor, motdelafin, args);
    }

    /**
     * Creates a new ZActor.
     *
     * @param context
     *            the main context used. If null, a new context will be created
     *            and closed at the stop of the operation.
     * <b>If not null, it is the responsibility of the caller to close it.</b>
     *
     * @param selector
     *            the creator of the selector used on the Plateau.
     * @param actor
     *            the actor handling messages from either stage and backstage
     * @param motdelafin
     *            the final word used to mark the end of the actor. Null to disable this mechanism.
     * @param args
     *            the optional arguments that will be passed to the distant actor
     * @deprecated use {@link ZActor#ZActor(ZContext, Actor, String, Object...)}
     */
    @Deprecated
    public ZActor(final ZContext context, final SelectorCreator selector, final Actor actor, final String motdelafin,
                  final Object... args)
    {
        this(context, actor, motdelafin, args);
    }

    /**
     * Creates a new ZActor.
     *
     * @param context
     *            the main context used. If null, a new context will be created
     *            and closed at the stop of the operation.
     * <b>If not null, it is the responsibility of the caller to close it.</b>
     *
     * @param actor
     *            the actor handling messages from either stage and backstage
     * @param motdelafin
     *            the final word used to mark the end of the actor. Null to disable this mechanism.
     * @param args
     *            the optional arguments that will be passed to the distant actor
     */
    public ZActor(final ZContext context, final Actor actor, final String motdelafin, final Object... args)
    {
        super(context, new ActorFortune(actor), motdelafin, args);
    }

    // actor creator
    private static final class ActorFortune implements Fortune
    {
        private final Actor actor;

        public ActorFortune(Actor actor)
        {
            Objects.requireNonNull(actor, "Actor shall be set to a non-null value");
            this.actor = actor;
        }

        @Override
        public String premiere(Socket mic, Object... args)
        {
            return actor.premiere(mic);
        }

        @Override
        public Star create(ZContext ctx, Socket pipe, int count, Star previous, Object... args)
        {
            return new ZActor.Double(ctx, pipe, actor, args);
        }

        @Override
        public boolean interview(Socket mic)
        {
            return actor.finished(mic);
        }

        @Override
        public void party(ZContext ctx)
        {
        }
    }

    // double for the loops, easing life for the actor
    private static final class Double implements EventsHandler, Star
    {
        // poller used for the loop
        private final ZPoller poller;

        // control pipe for Backstage side
        private final Socket pipe;

        // managed sockets
        private final List<Socket> sockets;

        // actor responsible for processing messages
        private final Actor actor;

        // context used for the closing of the sockets
        private final ZContext context;

        // creates a new double
        public Double(final ZContext ctx, final Socket pipe, final Actor actor, final Object... args)
        {
            this.context = ctx;
            this.pipe = pipe;
            this.actor = actor;

            final List<Socket> created = actor.createSockets(ctx, args);
            assert (created != null);

            sockets = new ArrayList<>(created);

            poller = new ZPoller(ctx);
            poller.setGlobalHandler(this);
        }

        // before starting the loops
        @Override
        public void prepare()
        {
            poller.register(pipe, ZPoller.POLLIN);
            actor.start(pipe, Collections.unmodifiableList(sockets), poller);
        }

        // gives the number of events to process
        @Override
        public int breathe()
        {
            long timeout = actor.looping(pipe, poller);
            return poller.poll(timeout);
            // events have been dispatched,
        }

        // acting takes place, return true to continue till the end
        @Override
        public boolean act(int events)
        {
            // act is actually already finished for handlers
            return events >= 0; //  Context is not shut down
        }

        // a loop just finished, return true to continue acting
        @Override
        public boolean entract()
        {
            return actor.looped(pipe, poller);
        }

        // destroys the double
        @Override
        public boolean renews()
        {
            // close every managed sockets
            Iterator<Socket> iter = sockets.iterator();
            while (iter.hasNext()) {
                final Socket socket = iter.next();
                iter.remove();
                if (socket != null) {
                    poller.unregister(socket);
                    socket.close();
                    // call back the actor to inform that a socket has been closed.
                    actor.closed(socket);
                }
            }
            // let the actor decide if the stage restarts a new double
            return actor.destroyed(context, pipe, poller);
        }

        @Override
        public boolean events(SelectableChannel channel, int events)
        {
            // TODO dispatch events from channels
            return true;
        }

        // an event has occurred on a registered socket
        @Override
        public boolean events(final Socket socket, final int events)
        {
            if (socket != pipe) {
                // Process a stage message, time to play
                return actor.stage(socket, pipe, poller, events);
            }
            else {
                // Process a control message, time to update your playing
                return actor.backstage(pipe, poller, events);
            }
        }
    }
}
