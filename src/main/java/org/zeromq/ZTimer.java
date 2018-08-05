package org.zeromq;

import zmq.util.Draft;
import zmq.util.Timers;

/**
 * Manages set of timers.
 *
 * Timers can be added with a given interval, when the interval of time expires after addition, handler method is executed with given arguments.
 * Timer is repetitive and will be executed over time until canceled.
 *
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class ZTimer
{
    /**
     * Opaque representation of a timer.
     */
    public static final class Timer
    {
        private final Timers.Timer delegate;

        Timer(Timers.Timer delegate)
        {
            this.delegate = delegate;
        }
    }

    /**
     * Called when the timer has been expired.
     */
    public interface Handler extends Timers.Handler
    {
    }

    private final Timers timer = new Timers();

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
        return new Timer(timer.add(interval, handler, args));
    }

    /**
     * Changes the interval of the timer.
     *
     * This method is slow, cancelling existing and adding a new timer yield better performance.
     * @param timer the timer to change the interval to.
     * @return true if set, otherwise false.
     */
    public boolean setInterval(Timer timer, long interval)
    {
        return this.timer.setInterval(timer.delegate, interval);
    }

    /**
     * Reset the timer.
     *
     * This method is slow, cancelling existing and adding a new timer yield better performance.
     * @param timer the timer to reset.
     * @return true if reset, otherwise false.
     */
    public boolean reset(Timer timer)
    {
        return this.timer.reset(timer.delegate);
    }

    /**
     * Cancel a timer.
     *
     * @param timer the timer to cancel.
     * @return true if cancelled, otherwise false.
     */
    public boolean cancel(Timer timer)
    {
        return this.timer.cancel(timer.delegate);
    }

    /**
     * Returns the time in millisecond until the next timer.
     *
     * @return the time in millisecond until the next timer.
     */
    public long timeout()
    {
        return timer.timeout();
    }

    /**
     * Execute the timers.
     *
     * @return the number of timers triggered.
     */
    public int execute()
    {
        return timer.execute();
    }

    /**
     * Sleeps until at least one timer can be executed and execute the timers.
     *
     * @return the number of timers triggered.
     */
    public int sleepAndExecute()
    {
        return timer.sleepAndExecute();
    }
}
