package org.jeromq.api;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * The ZLoop class provides an event-driven reactor pattern. The reactor
 * handles zmq.PollItem items (pollers or writers, sockets or fds), and
 * once-off or repeated timers. Its resolution is 1 msec. It uses a tickless
 * timer to reduce CPU interrupts in inactive processes.
 */
public class EventLoop {

    public static class EventLoopKiller extends RuntimeException {

    }

    /**
     * The type specifier here is the type of key that is assigned to the handler.
     */
    public static interface EventHandler<T> {
        void handle(EventLoop loop, Socket socket, T arg) throws EventLoopKiller;
    }

    private class InternalPoller<T> {
        private final Socket item;
        private final EventHandler<T> handler;
        private final T argument;

        private int numberOfErrors;                 //  If too many errors, kill poller
        private final EnumSet<PollOption> pollOptions;

        protected InternalPoller(Socket socket, EventHandler<T> handler, T argument, PollOption option1, PollOption... additionalOptions) {
            this.item = socket;
            this.handler = handler;
            this.argument = argument;
            this.numberOfErrors = 0;
            this.pollOptions = EnumSet.of(option1, additionalOptions);
        }
    }

    private class Timer<T> {
        private final int delay;
        private final EventHandler<T> handler;
        private final T argument;

        private int times;
        private long when;               //  Clock time when alarm goes off

        public Timer(int delay, int times, EventHandler<T> handler, T argument) {
            this.delay = delay;
            this.times = times;
            this.handler = handler;
            this.argument = argument;
            this.when = -1;
        }

        @Override
        public String toString() {
            return "Timer{" +
                    "delay=" + delay +
                    ", handler=" + handler +
                    ", argument=" + argument +
                    ", times=" + times +
                    ", when=" + when +
                    '}';
        }
    }

    private final List<InternalPoller> internalPollers = new ArrayList<InternalPoller>();        //  List of poll items
    private final List<Timer> timers = new ArrayList<Timer>();          //  List of timers
    private int numberOfPollers;                      //  Size of poll set
    private final List<InternalPoller> activeInternalPollers = new ArrayList<InternalPoller>();                  //  Pollers for this poll set
    private boolean dirty;                      //  True if pollItems needs rebuilding
    private boolean verbose;                    //  True if verbose tracing wanted
    private final List<Object> zombies = new ArrayList<Object>();         //  List of timers to kill
    private final List<Timer> newTimers = new ArrayList<Timer>();       //  List of timers to add

    public void destroy() {
        // do nothing
    }

    //  We hold an array of pollers that matches the pollItems, so we can
    //  register/cancel pollers orthogonally to executing the pollItems
    //  activity on pollers. Returns 0 on success, -1 on failure.

    private void rebuild() {
        numberOfPollers = internalPollers.size();

        activeInternalPollers.clear();
        activeInternalPollers.addAll(internalPollers);

        dirty = false;
    }

    private long ticklessTimer() {
        //  Calculate tickless timer, up to 1 hour
        long tickless = System.currentTimeMillis() + 1000 * 3600;
        for (Timer timer : timers) {
            if (timer.when == -1) {
                timer.when = timer.delay + System.currentTimeMillis();
            }
            if (tickless > timer.when) {
                tickless = timer.when;
            }
        }
        long timeout = tickless - System.currentTimeMillis();
        if (timeout < 0) {
            timeout = 0;
        }
        if (verbose) {
            System.out.printf("I: zloop: polling for %d msec\n", timeout);
        }
        return timeout;
    }

    /**
     * Register pollitem with the reactor for POLL_IN events. When the pollitem is ready, will call
     * the handler, passing the arg. Returns 0 if OK, -1 if there was an error.
     * If you register the pollitem more than once, each instance will invoke its
     * corresponding handler.
     */
    public <T> void addPoller(Socket socket, EventHandler<T> handler, T key) {
        addPoller(socket, handler, key, PollOption.POLL_IN);
    }

    /**
     * Register pollitem with the reactor for the specified events. When the pollitem is ready, will call
     * the handler, passing the arg. Returns 0 if OK, -1 if there was an error.
     * If you register the pollitem more than once, each instance will invoke its
     * corresponding handler.
     */
    public <T> void addPoller(Socket socket, EventHandler<T> handler, T key, PollOption option1, PollOption... additionalOptions) {
        InternalPoller<T> internalPoller = new InternalPoller<T>(socket, handler, key, option1, additionalOptions);
        internalPollers.add(internalPoller);
        dirty = true;

        if (verbose) {
            System.out.printf("I: zloop: register %s poller\n", socket.getType());
        }
    }

    /**
     * Cancel a pollitem from the reactor, specified by socket or FD. If both
     * are specified, uses only socket. If multiple poll items exist for same
     * socket/FD, cancels ALL of them.
     */
    public void stopPolling(Socket socket) {
        Iterator<InternalPoller> it = internalPollers.iterator();
        while (it.hasNext()) {
            InternalPoller internalPoller = it.next();
            if (socket == internalPoller.item) {
                it.remove();
                dirty = true;
            }
        }
        if (verbose) {
            System.out.printf("I: zloop: cancel %s poller", socket);
        }

    }

    /**
     * Register a timer that expires after some delay and repeats some number of
     * times. At each expiry, will call the handler, passing the arg. To
     * run a timer forever, use 0 times. Returns 0 if OK, -1 if there was an
     * error.
     */
    public <T> void createTimer(int delay, int times, EventHandler<T> handler, T arg) {
        Timer<T> timer = new Timer<T>(delay, times, handler, arg);

        //  We cannot touch self->timers because we may be executing that
        //  from inside the poll loop. So, we hold the new timer on the newTimers
        //  list, and process that list when we're done executing timers.
        newTimers.add(timer);
        if (verbose) {
            System.out.printf("I: zloop: register timer delay=%d times=%d when=%d\n", delay, times, timer.when);
        }
    }

    /**
     * Cancel all timers for a specific argument (as provided in zloop_timer)
     * Returns 0 on success.
     */
    public void endTimer(Object arg) {
        //  We cannot touch self->timers because we may be executing that
        //  from inside the poll loop. So, we hold the arg on the zombie
        //  list, and process that list when we're done executing timers.
        zombies.add(arg);
        if (verbose) {
            System.out.printf("I: zloop: cancel timer\n");
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Start the reactor. Takes control of the thread and returns when the 0MQ
     * context is terminated or the process is interrupted, or any event handler
     * throws the KillEventLoop exception. Event handlers may register new sockets and timers, and
     * cancel sockets. Returns 0 if interrupted, -1 if cancelled by a
     * handler, positive on internal error
     */
    public void start() {
        timers.addAll(newTimers);
        newTimers.clear();

        //  Recalculate all timers now
        long currentTime = System.currentTimeMillis();
        for (Timer timer : timers) {
            timer.when = timer.delay + currentTime;
        }

        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //  Main reactor loop
        boolean kill = false;
        while (!Thread.currentThread().isInterrupted()) {
            org.jeromq.api.Poller poller = new org.jeromq.api.Poller(selector);
            if (dirty) {
                rebuild();
            }
            for (InternalPoller socket : activeInternalPollers) {
                poller.register(socket.item, socket.pollOptions);
            }
            long wait = ticklessTimer();
            //  Handle any timers that have now expired
            Iterator<Timer> timerIterator = timers.iterator();
            while (timerIterator.hasNext()) {
                Timer timer = timerIterator.next();
                if (timer.when != -1 && System.currentTimeMillis() >= timer.when) {
                    if (verbose) {
                        System.out.println("I: zloop: call timer handler");
                    }
                    try {
                        timer.handler.handle(this, null, timer.argument);
                    } catch (EventLoopKiller eventLoopKiller) {
                        kill = true;
                    }

                    if (--timer.times == 0) {
                        timerIterator.remove();
                    } else {
                        timer.when = timer.delay + System.currentTimeMillis();
                    }
                }
            }
            if (kill) {
                break;
            }
            poller.poll(wait);
            //  Handle any pollers that are ready
            for (int itemNumber = 0; itemNumber < numberOfPollers; itemNumber++) {
                InternalPoller internalPoller = activeInternalPollers.get(itemNumber);
                if (poller.signaledForError(internalPoller.item)) {
                    if (verbose) {
                        System.out.printf("I: zloop: can't poll %s socket: %d", internalPoller.item, zmq.ZError.errno());
                    }
                    //  Give handler one chance to handle error, then kill
                    //  poller because it'll disrupt the reactor otherwise.
                    if (internalPoller.numberOfErrors++ > 0) {
                        stopPolling(internalPoller.item);
                    }
                } else {
                    internalPoller.numberOfErrors = 0;     //  A non-error happened
                }

                if (poller.signaledForInput(internalPoller.item)) {
                    if (verbose) {
                        System.out.printf("I: zloop: call %s socket handler\n", internalPoller.item);
                    }
                    try {
                        internalPoller.handler.handle(this, internalPoller.item, internalPoller.argument);
                    } catch (EventLoopKiller eventLoopKiller) {
                        kill = true;
                    }
                }
            }
            if (kill) {
                break;
            }


            //  Now handle any timer zombies
            //  This is going to be slow if we have many zombies
            for (Object arg : zombies) {
                timerIterator = timers.iterator();
                while (timerIterator.hasNext()) {
                    Timer timer = timerIterator.next();
                    if (timer.argument == arg) {
                        timerIterator.remove();
                    }
                }
            }
            //  Now handle any new timers added inside the loop
            timers.addAll(newTimers);
            newTimers.clear();
        }

        try {
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
