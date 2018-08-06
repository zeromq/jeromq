package zmq.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import zmq.ZMQ;

/**
 * Manages set of timers.
 *
 * Timers can be added with a given interval, when the interval of time expires after addition, handler method is executed with given arguments.
 * Timer is repetitive and will be executed over time until canceled.
 *
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class Timers
{
    private static final long ONE_MILLISECOND = 1;

    public static final class Timer
    {
        private long           interval;
        private final Handler  handler;
        private final Object[] args;

        public Timer(long interval, Handler handler, Object... args)
        {
            this.interval = interval;
            this.handler = handler;
            this.args = args;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(args);
            result = prime * result + ((handler == null) ? 0 : handler.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Timer other = (Timer) obj;
            if (handler == null) {
                if (other.handler != null) {
                    return false;
                }
            }
            else if (!handler.equals(other.handler)) {
                return false;
            }
            if (!Arrays.equals(args, other.args)) {
                return false;
            }
            return true;
        }
    }

    public interface Handler
    {
        void time(Object... args);
    }

    private final MultiMap<Long, Timer> timers          = new MultiMap<>();
    private final Set<Timer>            cancelledTimers = new HashSet<>();

    private long now()
    {
        return Clock.nowNS();
    }

    private boolean insert(Timer timer)
    {
        return timers.insert(now() + timer.interval, timer);
    }

    /**
     * Add timer to the set, timer repeats forever, or until cancel is called.
     * @param interval the interval of repetition in milliseconds.
     * @param handler the callback called at the expiration of the timer.
     * @param args the optional arguments for the handler.
     * @return an opaque handle for further cancel.
     */
    public Timer add(long interval, Handler handler, Object... args)
    {
        if (handler == null) {
            return null;
        }
        final Timer timer = new Timer(TimeUnit.MILLISECONDS.toNanos(interval), handler, args);
        final boolean rc = insert(timer);
        assert (rc);
        return timer;
    }

    /**
     * Changes the interval of the timer.
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to change the interval to.
     * @return true if set, otherwise false.
     */
    public boolean setInterval(Timer timer, long interval)
    {
        if (timers.remove(timer)) {
            timer.interval = TimeUnit.MILLISECONDS.toNanos(interval);
            return insert(timer);
        }
        return false;
    }

    /**
     * Reset the timer.
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to reset.
     * @return true if reset, otherwise false.
     */
    public boolean reset(Timer timer)
    {
        if (timers.contains(timer)) {
            return insert(timer);
        }
        return false;
    }

    /**
     * Cancel a timer.
     * @param timer the timer to cancel.
     * @return true if cancelled, otherwise false.
     */
    public boolean cancel(Timer timer)
    {
        if (timers.contains(timer)) {
            return cancelledTimers.add(timer);
        }
        return false;
    }

    /**
     * Returns the time in millisecond until the next timer.
     * @return the time in millisecond until the next timer.
     */
    public long timeout()
    {
        final long now = now();
        for (Entry<Timer, Long> entry : timers.entries()) {
            final Timer timer = entry.getKey();
            final Long timeout = entry.getValue();

            if (!cancelledTimers.remove(timer)) {
                //  Live timer, lets return the timeout
                if (timeout - now > 0) {
                    return ONE_MILLISECOND + TimeUnit.NANOSECONDS.toMillis(timeout - now);
                }
                else {
                    return 0;
                }
            }

            //  Remove it from the list of active timers.
            timers.remove(timeout, timer);
        }
        //  Wait forever as no timers are alive
        return -1;
    }

    /**
     * Execute the timers.
     * @return the number of timers triggered.
     */
    public int execute()
    {
        int executed = 0;
        final long now = now();
        for (Entry<Timer, Long> entry : timers.entries()) {
            final Timer timer = entry.getKey();
            final Long timeout = entry.getValue();

            //  Dead timer, lets remove it and continue
            if (cancelledTimers.remove(timer)) {
                //  Remove it from the list of active timers.
                timers.remove(timeout, timer);
                continue;
            }
            //  Map is ordered, if we have to wait for current timer we can stop.
            if (timeout - now > 0) {
                break;
            }

            insert(timer);

            timer.handler.time(timer.args);
            ++executed;
        }
        return executed;
    }

    public int sleepAndExecute()
    {
        long timeout = timeout();
        while (timeout > 0) {
            ZMQ.msleep(timeout);
            timeout = timeout();
        }
        return execute();
    }
}
