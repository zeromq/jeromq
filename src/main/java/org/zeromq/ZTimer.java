package org.zeromq;

import zmq.util.Draft;

/**
 * Manages set of timers.
 *
 * Timers can be added with a given interval, when the interval of time expires after addition, handler method is executed with given arguments.
 * Timer is repetitive and will be executed over time until canceled.
 *
 * This is a DRAFT class, and may change without notice.
 * @deprecated scheduled for removal in future release. Please use {@link org.zeromq.timer.ZTimer} instead
 */
@Draft
@Deprecated
public final class ZTimer
{
    /**
     * Opaque representation of a timer.
     * @deprecated use {@link org.zeromq.timer.ZTimer.Timer} instead
     */
    @Deprecated
    public static final class Timer
    {
        private final org.zeromq.timer.ZTimer.Timer delegate;

        Timer(org.zeromq.timer.ZTimer.Timer delegate)
        {
            this.delegate = delegate;
        }
    }

    /**
     * @deprecated use {@link org.zeromq.timer.TimerHandler} instead
     */
    @Deprecated
    public interface Handler extends org.zeromq.timer.TimerHandler
    {
    }

    private final org.zeromq.timer.ZTimer timer = new org.zeromq.timer.ZTimer();

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
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to change the interval to.
     * @return true if set, otherwise false.
     * @deprecated use {@link org.zeromq.timer.ZTimer.Timer#setInterval(long)} instead
     */
    @Deprecated
    public boolean setInterval(Timer timer, long interval)
    {
        return timer.delegate.setInterval(interval);
    }

    /**
     * Reset the timer.
     *
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to reset.
     * @return true if reset, otherwise false.
     * @deprecated use {@link org.zeromq.timer.ZTimer.Timer#reset()} instead
     */
    @Deprecated
    public boolean reset(Timer timer)
    {
        return timer.delegate.reset();
    }

    /**
     * Cancel a timer.
     *
     * @param timer the timer to cancel.
     * @return true if cancelled, otherwise false.
     * @deprecated use {@link org.zeromq.timer.ZTimer.Timer#cancel()} instead
     */
    @Deprecated
    public boolean cancel(Timer timer)
    {
        return timer.delegate.cancel();
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
