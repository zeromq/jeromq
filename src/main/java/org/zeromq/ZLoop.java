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
package org.zeromq;

import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The ZLoop class provides an event-driven reactor pattern. The reactor
 * handles zmq.PollItem items (pollers or writers, sockets or fds), and
 * once-off or repeated timers. Its resolution is 1 msec. It uses a tickless
 * timer to reduce CPU interrupts in inactive processes.
 */

public class ZLoop
{

    public static interface IZLoopHandler
    {
        public int handle(ZLoop loop, PollItem item, Object arg);
    }

    private class SPoller
    {
        PollItem item;
        IZLoopHandler handler;
        Object arg;
        int errors;                 //  If too many errors, kill poller

        protected SPoller(PollItem item, IZLoopHandler handler, Object arg)
        {
            this.item = item;
            this.handler = handler;
            this.arg = arg;
            errors = 0;
        }

    }

    ;

    private class STimer
    {
        int delay;
        int times;
        IZLoopHandler handler;
        Object arg;
        long when;               //  Clock time when alarm goes off

        public STimer(int delay, int times, IZLoopHandler handler,
                      Object arg)
        {
            this.delay = delay;
            this.times = times;
            this.handler = handler;
            this.arg = arg;
            this.when = -1;
        }

    }

    private final List<SPoller> pollers;        //  List of poll items
    private final List<STimer> timers;          //  List of timers
    private int pollSize;                       //  Size of poll set
    private Poller pollset;                     //  zmq_poll set
    private SPoller[] pollact;                  //  Pollers for this poll set
    private boolean dirty;                      //  True if pollset needs rebuilding
    private boolean verbose;                    //  True if verbose tracing wanted
    private final List<Object> zombies;         //  List of timers to kill
    private final List<STimer> newTimers;       //  List of timers to add

    public ZLoop()
    {
        pollers = new ArrayList<SPoller>();
        timers = new ArrayList<STimer>();
        zombies = new ArrayList<Object>();
        newTimers = new ArrayList<STimer>();
    }

    public void destroy()
    {
        // do nothing
    }

    //  We hold an array of pollers that matches the pollset, so we can
    //  register/cancel pollers orthogonally to executing the pollset
    //  activity on pollers. Returns 0 on success, -1 on failure.

    private void rebuild()
    {
        pollact = null;

        pollSize = pollers.size();
        pollset = new Poller(pollSize);

        pollact = new SPoller[pollSize];

        int itemNbr = 0;
        for (SPoller poller : pollers) {
            pollset.register(poller.item);
            pollact[itemNbr] = poller;
            itemNbr++;
        }
        dirty = false;
    }

    private long ticklessTimer()
    {
        //  Calculate tickless timer, up to 1 hour
        long tickless = System.currentTimeMillis() + 1000 * 3600;
        for (STimer timer : timers) {
            if (timer.when == -1)
                timer.when = timer.delay + System.currentTimeMillis();
            if (tickless > timer.when)
                tickless = timer.when;
        }
        long timeout = tickless - System.currentTimeMillis();
        if (timeout < 0)
            timeout = 0;
        if (verbose)
            System.out.printf("I: zloop: polling for %d msec\n", timeout);
        return timeout;
    }
    //  --------------------------------------------------------------------------
    //  Register pollitem with the reactor. When the pollitem is ready, will call
    //  the handler, passing the arg. Returns 0 if OK, -1 if there was an error.
    //  If you register the pollitem more than once, each instance will invoke its
    //  corresponding handler.

    public int addPoller(PollItem item_, IZLoopHandler handler, Object arg)
    {

        PollItem item = item_;
        if (item.getRawSocket() == null && item.getSocket() == null)
            return -1;

        SPoller poller = new SPoller(item_, handler, arg);
        pollers.add(poller);

        dirty = true;
        if (verbose)
            System.out.printf("I: zloop: register %s poller (%s, %s)\n",
                    item.getSocket() != null ? item.getSocket().getType() : "RAW",
                    item.getSocket(), item.getRawSocket());
        return 0;
    }

    //  --------------------------------------------------------------------------
    //  Cancel a pollitem from the reactor, specified by socket or FD. If both
    //  are specified, uses only socket. If multiple poll items exist for same
    //  socket/FD, cancels ALL of them.

    public void removePoller(PollItem item_)
    {
        PollItem item = item_;

        Iterator<SPoller> it = pollers.iterator();
        while (it.hasNext()) {
            SPoller p = it.next();
            if (item.equals(p.item)) {
                it.remove();
                dirty = true;
            }
        }
        if (verbose)
            System.out.printf("I: zloop: cancel %s poller (%s, %s)",
                    item.getSocket() != null ? item.getSocket().getType() : "RAW",
                    item.getSocket(), item.getRawSocket());

    }

    //  --------------------------------------------------------------------------
    //  Register a timer that expires after some delay and repeats some number of
    //  times. At each expiry, will call the handler, passing the arg. To
    //  run a timer forever, use 0 times. Returns 0 if OK, -1 if there was an
    //  error.

    public int addTimer(int delay, int times, IZLoopHandler handler, Object arg)
    {
        STimer timer = new STimer(delay, times, handler, arg);

        //  We cannot touch self->timers because we may be executing that
        //  from inside the poll loop. So, we hold the new timer on the newTimers
        //  list, and process that list when we're done executing timers.
        newTimers.add(timer);
        if (verbose)
            System.out.printf("I: zloop: register timer delay=%d times=%d\n", delay, times);

        return 0;
    }

    //  --------------------------------------------------------------------------
    //  Cancel all timers for a specific argument (as provided in zloop_timer)
    //  Returns 0 on success.

    public int removeTimer(Object arg)
    {
        assert (arg != null);

        //  We cannot touch self->timers because we may be executing that
        //  from inside the poll loop. So, we hold the arg on the zombie
        //  list, and process that list when we're done executing timers.
        zombies.add(arg);
        if (verbose)
            System.out.printf("I: zloop: cancel timer\n");

        return 0;
    }

    //  --------------------------------------------------------------------------
    //  Set verbose tracing of reactor on/off
    public void verbose(boolean verbose)
    {
        this.verbose = verbose;
    }

    //  --------------------------------------------------------------------------
    //  Start the reactor. Takes control of the thread and returns when the 0MQ
    //  context is terminated or the process is interrupted, or any event handler
    //  returns -1. Event handlers may register new sockets and timers, and
    //  cancel sockets. Returns 0 if interrupted, -1 if cancelled by a
    //  handler, positive on internal error

    public int start()
    {
        int rc = 0;

        timers.addAll(newTimers);
        newTimers.clear();

        //  Recalculate all timers now
        for (STimer timer : timers) {
            timer.when = timer.delay + System.currentTimeMillis();
        }

        //  Main reactor loop
        while (!Thread.currentThread().isInterrupted()) {
            if (dirty) {
                // If s_rebuild_pollset() fails, break out of the loop and
                // return its error
                rebuild();
            }
            long wait = ticklessTimer();

            rc = pollset.poll(wait);

            if (rc == -1) {
                if (verbose)
                    System.out.printf("I: zloop: interrupted (%d)\n", rc);
                rc = 0;
                break;              //  Context has been shut down
            }
            //  Handle any timers that have now expired
            Iterator<STimer> it = timers.iterator();
            while (it.hasNext()) {
                STimer timer = it.next();
                if (System.currentTimeMillis() >= timer.when && timer.when != -1) {
                    if (verbose)
                        System.out.println("I: zloop: call timer handler");
                    rc = timer.handler.handle(this, null, timer.arg);
                    if (rc == -1)
                        break;      //  Timer handler signalled break
                    if (timer.times != 0 && --timer.times == 0) {
                        it.remove();
                    } else
                        timer.when = timer.delay + System.currentTimeMillis();
                }
            }
            if (rc == -1)
                break; // Some timer signalled break from the reactor loop

            //  Handle any pollers that are ready
            for (int itemNbr = 0; itemNbr < pollSize; itemNbr++) {
                SPoller poller = pollact[itemNbr];
                if (pollset.getItem(itemNbr).isError()) {
                    if (verbose)
                        System.out.printf("I: zloop: can't poll %s socket (%s, %s)\n",
                                poller.item.getSocket() != null ? poller.item.getSocket().getType() : "RAW",
                                poller.item.getSocket(), poller.item.getRawSocket());
                    //  Give handler one chance to handle error, then kill
                    //  poller because it'll disrupt the reactor otherwise.
                    if (poller.errors++ > 0) {
                        removePoller(poller.item);
                    }
                } else
                    poller.errors = 0;     //  A non-error happened

                if (pollset.getItem(itemNbr).readyOps() > 0) {
                    if (verbose)
                        System.out.printf("I: zloop: call %s socket handler (%s, %s)\n",
                                poller.item.getSocket() != null ? poller.item.getSocket().getType() : "RAW",
                                poller.item.getSocket(), poller.item.getRawSocket());
                    rc = poller.handler.handle(this, poller.item, poller.arg);
                    if (rc == -1)
                        break;      //  Poller handler signalled break
                }
            }

            //  Now handle any timer zombies
            //  This is going to be slow if we have many zombies
            for (Object arg : zombies) {
                it = timers.iterator();
                while (it.hasNext()) {
                    STimer timer = it.next();
                    if (timer.arg == arg) {
                        it.remove();
                    }
                }
            }
            //  Now handle any new timers added inside the loop
            timers.addAll(newTimers);
            newTimers.clear();

            if (rc == -1)
                break;
        }

        return rc;
    }

}
