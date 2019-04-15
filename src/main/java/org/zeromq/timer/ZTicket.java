package org.zeromq.timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import zmq.util.Clock;
import zmq.util.Draft;
import zmq.util.Utils;
import zmq.util.function.Supplier;

/**
 * Manages set of tickets.
 * <p>
 * Ticket timers are very fast in the case where
 * you use a lot of timers (thousands), and frequently remove and add them.
 * The main use case is expiry timers for servers that handle many clients,
 * and which reset the expiry timer for each message received from a client.
 * Whereas normal timers perform poorly as the number of clients grows, the
 * cost of ticket timers is constant, no matter the number of clients
 * <p>
 * Tickets can be added with a given delay.
 * <br>
 * When the delay of time expires after addition, handler method is executed with given arguments.
 * <br>
 * Ticket is NOT repetitive and will be executed once unless canceled.
 * <p>
 * <strong>This class is not thread-safe</strong>
 * <p>
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class ZTicket
{
    /**
     * Opaque representation of a ticket.
     */
    public static final class Ticket implements Comparable<Ticket>
    {
        private final ZTicket      parent;
        private final TimerHandler handler;
        private final Object[]     args;
        private long               start;
        private long               delay;
        private boolean            alive = true;

        private Ticket(ZTicket parent, long now, long delay, TimerHandler handler, Object... args)
        {
            assert (args != null);
            this.parent = parent;
            this.start = now;
            this.delay = delay;
            this.handler = handler;
            this.args = args;
        }

        /**
         * Resets the ticket.
         */
        public void reset()
        {
            if (alive) {
                parent.sort = true;
                start = parent.now();
            }
        }

        /**
         * Cancels a ticket.
         * @return true if cancelled, false if already cancelled.
         */
        public boolean cancel()
        {
            if (alive) {
                alive = false;
                parent.sort = true;
                return true;
            }
            return false;
        }

        /**
         * Changes the delay of the ticket.
         * @param delay the new delay of the ticket.
         */
        public void setDelay(long delay)
        {
            if (alive) {
                parent.sort = true;
                this.delay = delay;
            }
        }

        @Override
        public int compareTo(Ticket other)
        {
            if (alive) {
                if (other.alive) {
                    return Long.valueOf(start - other.start).compareTo(other.delay - delay);
                }
                return -1;
            }
            return other.alive ? 1 : 0;
        }
    }

    private final List<Ticket> tickets;

    private final Supplier<Long> clock;

    private boolean sort;

    public ZTicket()
    {
        this(() -> TimeUnit.NANOSECONDS.toMillis(Clock.nowNS()));
    }

    ZTicket(Supplier<Long> clock)
    {
        this(clock, new ArrayList<>());
    }

    ZTicket(Supplier<Long> clock, List<Ticket> tickets)
    {
        this.clock = clock;
        this.tickets = tickets;
    }

    private long now()
    {
        return clock.get();
    }

    private void insert(Ticket ticket)
    {
        sort = tickets.add(ticket);
    }

    /**
     * Add ticket to the set.
     * @param delay the expiration delay in milliseconds.
     * @param handler the callback called at the expiration of the ticket.
     * @param args the optional arguments for the handler.
     * @return an opaque handle for further cancel and reset.
     */
    public Ticket add(long delay, TimerHandler handler, Object... args)
    {
        if (handler == null) {
            return null;
        }
        Utils.checkArgument(delay > 0, "Delay of a ticket has to be strictly greater than 0");
        final Ticket ticket = new Ticket(this, now(), delay, handler, args);
        insert(ticket);
        return ticket;
    }

    /**
     * Returns the time in millisecond until the next ticket.
     * @return the time in millisecond until the next ticket.
     */
    public long timeout()
    {
        if (tickets.isEmpty()) {
            return -1;
        }
        sortIfNeeded();
        //  Tickets are sorted, so check first ticket
        Ticket first = tickets.get(0);
        return first.start - now() + first.delay;
    }

    /**
     * Execute the tickets.
     * @return the number of tickets triggered.
     */
    public int execute()
    {
        int executed = 0;
        final long now = now();
        sortIfNeeded();
        Set<Ticket> cancelled = new HashSet<>();
        for (Ticket ticket : this.tickets) {
            if (now - ticket.start < ticket.delay) {
                // tickets are ordered, not meeting the condition means the next ones do not as well
                break;
            }
            if (!ticket.alive) {
                //  Dead ticket, let's continue
                cancelled.add(ticket);
                continue;
            }
            ticket.alive = false;
            cancelled.add(ticket);
            ticket.handler.time(ticket.args);
            ++executed;
        }
        for (int idx = tickets.size(); idx-- > 0; ) {
            Ticket ticket = tickets.get(idx);
            if (ticket.alive) {
                break;
            }
            cancelled.add(ticket);
        }
        this.tickets.removeAll(cancelled);
        cancelled.clear();
        return executed;
    }

    private void sortIfNeeded()
    {
        if (sort) {
            sort = false;
            Collections.sort(tickets);
        }
    }
}
