package org.zeromq.timer;

import org.zeromq.timer.ZTicket.Ticket;
import org.zeromq.timer.ZTimer.Timer;

import zmq.util.Draft;
import zmq.util.function.Supplier;

/**
 * Manages set of tickets and timers.
 * <p>
 * Tickets can be added with a given delay in milliseconds,
 * when the delay expires after addition,
 * handler method is executed with given arguments.
 * <br>
 * Ticket is NOT repetitive and will be executed once unless canceled.
 * <p>
 * Timers can be added with a given interval in milliseconds,
 * when the interval of time expires after addition,
 * handler method is executed with given arguments.
 * <br>
 * Timer is repetitive and will be executed over time until canceled.
 * <p>
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class ZTicker
{
    private final ZTimer  timer;
    private final ZTicket ticket;

    public ZTicker()
    {
        timer = new ZTimer();
        ticket = new ZTicket();
    }

    ZTicker(Supplier<Long> clock)
    {
        timer = new ZTimer(clock);
        ticket = new ZTicket(clock);
    }

    public Ticket addTicket(long interval, TimerHandler handler, Object... args)
    {
        return ticket.add(interval, handler, args);
    }

    public Timer addTimer(long interval, TimerHandler handler, Object... args)
    {
        return timer.add(interval, handler, args);
    }

    public long timeout()
    {
        long timer = this.timer.timeout();
        long ticket = this.ticket.timeout();
        if (timer < 0 || ticket < 0) {
            return Math.max(timer, ticket);
        }
        else {
            return Math.min(timer, ticket);
        }
    }

    public int execute()
    {
        return timer.execute() + ticket.execute();
    }
}
