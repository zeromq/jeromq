package org.zeromq;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZThread.IAttachedRunnable;

/**
 * First implementation for the base of a remotely controlled background service for 0MQ.
 *
 * <p>
 * A side or endpoint designates the same thing: the thread where lives one of the two parts of the system.
 * <br/>
 * A {@link ZStar Star} has 2 sides (with a trial to use theater terms for fun (: and hopefully clarity :)
 * <br/>
 * - the Corbeille side, or control side
 * <br/>
 * This is where one can {@link ZAgent#send(ZMsg) send} and {@link ZAgent#recv() receive} control messages to the underneath star via the ZStar agent.
 * <br/>
 * The ZStar lives on this side and is a way to safely communicate with the distant provided star.
 * <br/>
 * Note: Corbeille is a french word designing the most privileged places in the theater where the King was standing,
 * 1st floor, just above the orchestra (Wikipedia...).
 *
 * <p>
 * - the Plateau side, or background side where all the performances are made by the provided star.
 * <br/>
 * The provided star is living on the Plateau.
 * <br/>
 * The Plateau is made of the Stage where effective acting takes place and of the Wings
 * where the provided star can perform control decisions to stop or continue the acting.
 * <br/>
 * From this side, the work is done via callbacks using the template-method pattern, applied with interfaces (?)
 * <p>
 * Communication between the 2 sides is ensured by {@link #pipe() pipes} that <b>SHALL NEVER</b> be mixed between background and control sides.
 * <br/>
 * Instead, each side use its own pipe to communicate with the other one, transmitting only 0MQ messages.
 * The main purpose of this class is to ease this separation and to provide contracts, responsibilities and action levers for each side.
 * <br/>
 * For example, instead of using pipe() which is useful to have more control, fast users would want to call {@link #agent()} to get the agent
 * or directly call {@link #send(ZMsg)} or {@link #recv()} from the ZStar as the ZStar is itself an agent!
 * <p>
 * The ZStar takes care of the establishment of the background processing, calling the provided star
 * at appropriate times. It can also manage the {@link ZAgent#sign() exited} state on the control side,
 * if providing a non-null "Mot de la Fin".
 * <br/>
 * It also takes care of the closing of sockets and context if it had to create one.
 * <p>
 * A {@link Star star} is basically a contract interface that anyone who uses this ZStar to create a background service SHALL comply to.<br/>
 * <p>
 * PS: Je sais qu'il y a une différence entre acteur et comédien :)
 * PPS: I know nothing about theater!
 * <p>
 * For an example of code, please refer to the {@link ZActor} source code.
 *
 */
// remote controlled background message processing API for 0MQ.
public class ZStar implements ZAgent
{
    /**
     * Contract interface when acting in plain sight.
     */
    // contract interface for acting with the spot lights on
    public interface Star
    {
        /**
         * Called when the star is in the wings.<br/>
         * Hint:       Can be used to initialize the service, or ...<br/>
         * Key point:  no loop has started already.
         */
        void prepare();

        /**
         * Called when the star in on stage, just before acting.<br/>
         * Hint:       Can be used to poll events or get input/events from other sources, or ...<br/>
         * Key point:  a loop just started.
         *
         * @return the number of events to process
         */
        int breathe();

        /**
         * Where acting takes place ...<br/>
         * Hint:       Can be used to process the events or input acquired from the previous step, or ...<br/>
         * Key point:  in the middle of a loop.<br/>
         * Decision:   to act on the next loop or not
         *
         * @param events the number of events to process
         * @return true to continue till the end of the act, false to stop loopS here.
         */
        boolean act(int events);

        /**
         * Called as an interval between each act.<br/>
         * Hint:       Can be used to perform decisions to continue next loop or not, or to send computed data to outputs, or ...<br/>
         * Key point:  at the end of a loop.<br/>
         * Decision:   to act on the next loop or not
         *
         * @return true to continue acting, false to stop loopS here.
         */
        boolean entract();

        /**
         * Does the star want to renew for a new performance ?
         * Hint:       Can be used to perform decisions to continue looping or not, or to send computed data to outputs, or ...<br/>
         * Key point:  the inner looping mechanism just ended<br/>
         * Decision:   to exit or not
         *
         * @return true to restart acting, false to leave here
         */
        boolean renews();
    }

    /**
     * Utility class with callback for when the Star has finished its performances.
     */
    public interface TimeTaker
    {
        /**
         * Called when the show is finished but no cleaning is still done.
         * Useful to make the background thread wait a little bit, for example.
         *
         * @param ctx the shadow context
         */
        void party(ZContext ctx);

    }

    // party time easily done. Wait for the specified amount of time
    public static void party(long time, TimeUnit unit)
    {
        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(time, unit));
    }

    // contract for a creator of stars
    public interface Fortune extends TimeTaker
    {
        /**
         * This is the grand premiere!
         * Called when the star enters the plateau.
         * The show is about to begin. Inform the public about that.
         * Called before the creation of the first star and its sockets
         *
         * @param mic   the pipe to the Corbeille side
         * @param args  the arguments passed as parameters of the star constructor
         *
         * @return the name of the upcoming performance.
         */
        String premiere(Socket mic, Object... args);

        /**
         * Creates a star.
         *
         * @param ctx       the context used for the creation of the sockets
         * @param mic       the pipe to the Corbeille side
         * @param sel       the selector used for polling
         * @param count     the number of times a star was created.
         * @param previous  the previous star if any (null at the first creation)
         * @param args      the arguments passed as parameters of the star constructor
         *
         * @return a new star is born!
         */
        Star create(ZContext ctx, Socket mic, Selector sel, int count, Star previous, Object... args);

        /**
         * The show is over.
         * Called when the show is over.
         *
         * @param mic  the pipe to the Corbeille side
         * @return true to allow to spread the word and close all future communications
         */
        boolean interview(Socket mic);

    }

    /**
     * Control for the end of the remote operations.
     */
    public interface Exit
    {
        /**
         * Causes the current thread to wait in blocking mode until the end of the remote operations.
         */
        void awaitSilent();

        /**
         * Causes the current thread to wait in blocking mode until the end of the remote operations,
         * unless the thread is interrupted.
         *
         * <p>If the current thread:
         * <ul>
         * <li>has its interrupted status set on entry to this method; or
         * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
         * </ul>
         * then {@link InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.
         *
         * @throws InterruptedException if the current thread is interrupted
         *         while waiting
         */
        void await() throws InterruptedException;

        /**
         * Causes the current thread to wait in blocking mode until the end of the remote operations,
         * unless the thread is interrupted, or the specified waiting time elapses.
         *
         * <p>If the current thread:
         * <ul>
         * <li>has its interrupted status set on entry to this method; or
         * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
         * </ul>
         * then {@link InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.
         *
         * <p>If the specified waiting time elapses then the value {@code false}
         * is returned.  If the time is less than or equal to zero, the method
         * will not wait at all.
         *
         * @param timeout the maximum time to wait
         * @param unit the time unit of the {@code timeout} argument
         * @return {@code true} if the remote operations ended and {@code false}
         *         if the waiting time elapsed before the remote operations ended
         * @throws InterruptedException if the current thread is interrupted
         *         while waiting
         */
        boolean await(long timeout, TimeUnit unit) throws InterruptedException;

        /**
         * Checks in non-blocking mode, if the remote operations have ended.
         * @return true if the runnable where the remote operations occurred if finished, otherwise false.
         */
        boolean isExited();
    }

    /**
     * Returns the Corbeille endpoint.
     * Can be used to send or receive control messages to the distant star via Backstage.
     *
     * @return the agent/mailbox used to send and receive control messages to and from the star.
     */
    public ZAgent agent()
    {
        return agent;
    }

    /**
     * Returns the control of the proper exit of the remote operations.
     * @return a structure used for checking the end of the remote operations.
     */
    public Exit exit()
    {
        return plateau;
    }

    /**
     * Creates a new ZStar.
     *
     * @param actor    the creator of stars on the Plateau
     * @param lock     the final word used to mark the end of the star. Null to disable this mechanism.
     * @param args     the optional arguments that will be passed to the distant star
     */
    public ZStar(Fortune actor, String lock, Object... args)
    {
        this(new VerySimpleSelectorCreator(), actor, lock, args);
    }

    /**
     * Creates a new ZStar.
     *
     * @param selector   the creator of the selector used on the Plateau.
     * @param fortune    the creator of stars on the Plateau
     * @param motdelafin the final word used to mark the end of the star. Null to disable this mechanism.
     * @param args       the optional arguments that will be passed to the distant star
     */
    public ZStar(SelectorCreator selector, Fortune fortune, String motdelafin, Object... args)
    {
        this(null, selector, fortune, motdelafin, args);
    }

    /**
     * Creates a new ZStar.
     *
     * @param context
     *            the main context used. If null, a new context will be created
     *            and closed at the stop of the operation.
     * <b>If not null, it is the responsibility of the caller to close it.</b>
     *
     * @param selector   the creator of the selector used on the Plateau.
     * @param fortune    the creator of stars on the Plateau
     * @param motdelafin the final word used to mark the end of the star. Null to disable this mechanism.
     * @param bags       the optional arguments that will be passed to the distant star
     */
    public ZStar(final ZContext context, final SelectorCreator selector, final Fortune fortune, String motdelafin,
            final Object... bags)
    {
        super();
        assert (fortune != null);
        // entering platform to load trucks

        // the story writer
        SelectorCreator feather = selector;
        if (selector == null) {
            // initialize selector
            feather = new VerySimpleSelectorCreator();
        }

        // initialize the context
        ZContext chef = context;
        ZContext producer = null;
        if (chef == null) {
            // no context provided, create one
            chef = new ZContext();
            // it will be destroyed, so this is the main context
            producer = chef;
        }
        this.context = chef;
        assert (this.context != null);

        // retrieve the last optional set and entourage given in input
        Set set = null;
        Entourage entourage = null;
        for (Object bag : bags) {
            if (bag instanceof Set) {
                set = (Set) bag;
            }
            if (bag instanceof Entourage) {
                entourage = (Entourage) bag;
            }
        }
        if (set == null) {
            set = new SimpleSet();
        }

        final List<Object> train = new ArrayList<>(6 + bags.length);

        train.add(set);
        train.add(fortune);
        train.add(feather);
        train.add(producer);
        train.add(entourage);
        train.add(motdelafin);
        // 6 mandatory wagons
        train.addAll(Arrays.asList(bags));

        // now going to the plateau
        Socket phone = ZThread.fork(chef, plateau, train.toArray());

        agent = agent(phone, motdelafin);
    }

    // context of the star. never null
    private final ZContext context;

    // communicating agent with the star for the Corbeille side
    private final ZAgent agent;

    // distant runnable where acting takes place
    private final Plateau plateau = new Plateau();

    /**
     * Creates a new agent for the star.
     *
     * This method is called in the constructor so don't rely on private members to do the job.
     * Apart from that, it is the last call so can be safely overridden from the ZStar point-of-view
     * BUT if you don't make it final, one of your subclass could make you misery...
     *
     * @param phone   the socket used to communicate with the star
     * @param secret  the specific keyword indicating the death of the star and locking the agent. Null to override the lock mechanism.
     * @return the newly created agent for the star.
     */
    protected ZAgent agent(Socket phone, String secret)
    {
        return ZAgent.Creator.create(phone, secret);
    }

    /**
     * @return the context. Never null. If provided context in constructor was null, returns the auto-generated context for that star.
     */
    private ZContext context()
    {
        return context;
    }

    // the plateau where the acting will take place (stage and backstage), or
    // the forked runnable containing the loop processing all messages in the background
    private static final class Plateau implements IAttachedRunnable, Exit
    {
        private static final AtomicInteger shows = new AtomicInteger();
        // id if unnamed
        private final int number = shows.incrementAndGet();

        // waiting-flag for the end of the remote operations
        private final CountDownLatch exit = new CountDownLatch(1);

        @Override
        public void run(final Object[] train, final ZContext chef, final Socket mic)
        {
            final int mandat = 6;

            // end of a trip can be a bit messy...
            Fortune star = (Fortune) train[1];

            final Entourage entourage = (Entourage) train[4];

            final ZContext producer = (ZContext) train[3];
            final SelectorCreator feather = (SelectorCreator) train[2];

            final Set set = (Set) train[0];
            // the word informing the world that the plateau is closed and the star vanished
            final String gossip = (String) train[5];

            // prune our mandatory transit variables from the arguments
            final Object[] bags = new Object[train.length - mandat];
            System.arraycopy(train, mandat, bags, 0, bags.length);

            // leaving unloaded platform
            if (entourage != null) {
                entourage.breakaleg(chef, star, mic, bags);
            }

            Selector story = null;
            // now entering artistic zone
            try {
                // create the selector used for polling operations
                story = feather.create();

                // Premiere !
                String name = star.premiere(mic, bags);
                // put the name of the performance on the front door with lightnings
                set.lights(name, number);

                // star is entering the wings
                showMustGoOn(chef, set, story, mic, star, bags);
                // star is leaving the plateau
            }
            catch (Throwable e) {
                // Who stole the story? There is no play if there is no story! C'est un scandale!
                e.printStackTrace();
                // TODO enhance error
            }
            finally {
                try {
                    // star is interviewed about this event
                    boolean tell = star.interview(mic);

                    if (tell && gossip != null) {
                        // inform the Corbeille side of the future closing of the plateau and the vanishing of the star
                        try {
                            mic.send(gossip);
                        }
                        catch (Exception e) {
                            // really ?
                            e.printStackTrace();
                        }
                    }

                    // we are not in a hurry at this point when cleaning up the remains of a good show ...
                    star.party(chef);
                    star = null;
                    if (entourage != null) {
                        entourage.party(chef);
                    }
                    // Sober again ...

                    // show is over, time to close
                    chef.close();
                    if (producer != null) {
                        // this is a self-generated context, destroy it
                        producer.close();
                    }
                    feather.destroy(story);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    // TODO enhance error
                }
                finally {
                    exit.countDown();
                }
            }
        }

        /******************************************************************************/
        /* TAP TAP TAP | | TAP | | TAP | | TAP | | | | | | | | | | | | | | | | | | | |*/
        /******************************************************************************/

        // starts the performance
        private void showMustGoOn(final ZContext chef, final Set set, final Selector story, final Socket phone,
                                  final Fortune fortune, final Object... bags)
        {
            int shows = 0;
            /** on the spot lights, the star in only an actor **/
            Star actor = null;
            /** double-while-loop enables the restarting of a new star for the same acting on the same stage **/
            /// so existing sockets can be closed and recreated in order to perform a cold restart or a stop **/
            do {
                actor = fortune.create(chef, phone, story, shows++, actor, bags);
                /** a new star is born! And an acting one! **/

                actor.prepare();
                /** actor is leaving the wings **/

                while (!set.fire()) {
                    /** actor decides when the act will begin **/
                    int events = actor.breathe();

                    /** perform a new act of the show **/
                    if (!actor.act(events)) {
                        // Context has been shut down
                        break;
                    }

                    /** end of the act, back to the wings **/
                    if (!actor.entract()) {
                        // star has decided to stop acting
                        break;
                    }
                }
            } while (actor.renews());
            // star is leaving the Plateau and the show
        }

        /******************************************************************************/
        /* | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |*/
        /******************************************************************************/
        // NB: never use green color on the stage floor of a french theater. Or something bad will happen...

        @Override
        public void awaitSilent()
        {
            try {
                exit.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void await() throws InterruptedException
        {
            exit.await();
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException
        {
            return exit.await(timeout, unit);
        }

        @Override
        public boolean isExited()
        {
            return exit.getCount() == 0;
        }
    }

    @Override
    public ZMsg recv()
    {
        return agent.recv();
    }

    @Override
    public ZMsg recv(int timeout)
    {
        return agent.recv(timeout);
    }

    @Override
    public ZMsg recv(boolean wait)
    {
        return agent.recv(wait);
    }

    @Override
    public boolean send(ZMsg message)
    {
        return agent.send(message);
    }

    @Override
    public boolean send(ZMsg msg, boolean destroy)
    {
        return agent.send(msg, destroy);
    }

    @Override
    public boolean send(String word)
    {
        return agent.send(word);
    }

    @Override
    public boolean send(String word, boolean more)
    {
        return agent.send(word, more);
    }

    @Override
    public Socket pipe()
    {
        return agent.pipe();
    }

    @Override
    public boolean sign()
    {
        return agent.sign();
    }

    @Override
    public void close()
    {
        agent.close();
    }

    public interface Set
    {
        /**
         * Puts the performance name on the front door with big lights.
         * @param name the name of the performance.
         * @param id the performance number.
         */
        void lights(String name, int id);

        /**
         * Is the set on fire ?
         * @return true if it is time to leave the place.
         */
        boolean fire();
    }

    public static class SimpleSet implements Set
    {
        @Override
        public boolean fire()
        {
            return Thread.currentThread().isInterrupted();
        }

        @Override
        public void lights(String name, int id)
        {
            if (name == null) {
                name = createDefaultName("Star-%d", id);
            }
            Thread.currentThread().setName(name);
        }

        public static String createDefaultName(final String format, final int id)
        {
            return String.format(format, id);
        }
    }

    /**
     * Utility class with calls surrounding the execution of the Star.
     */
    public interface Entourage extends TimeTaker
    {
        /**
         * Called when the show is about to start.
         * Can be a useful point in the whole process from time to time.
         *
         * @param ctx        the context provided in the creation step
         * @param fortune    the creator of stars
         * @param phone      the socket used to communicate with the Agent
         * @param bags       the optional arguments that were passed at the creation
         */
        void breakaleg(ZContext ctx, Fortune fortune, Socket phone, Object... bags);

        // well ...
    }
}
