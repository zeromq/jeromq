package org.zeromq.timer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.zeromq.timer.ZTicket.Ticket;

public class ZTicketTest
{
    final AtomicLong      time    = new AtomicLong();
    private ZTicket       tickets = new ZTicket(time::get);
    private AtomicInteger invoked = new AtomicInteger();

    private final TimerHandler handler = args -> {
        AtomicInteger invoked = (AtomicInteger) args[0];
        invoked.incrementAndGet();
    };

    private static final TimerHandler NOOP = args -> {
        // do nothing
    };

    @Before
    public void setup()
    {
        time.set(0);
        invoked = new AtomicInteger();
    }

    @Test
    public void testNoTicket()
    {
        assertThat(tickets.timeout(), is(-1L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidInsertion()
    {
        tickets.add(-1, NOOP);
    }

    @Test
    public void testSingleInsertion()
    {
        tickets.add(1, NOOP);
        assertThat(tickets.timeout(), is(1L));
    }

    @Test
    public void testMultipleInsertions()
    {
        tickets.add(2, NOOP);
        tickets.add(40, NOOP);
        tickets.add(30, NOOP);
        assertThat(tickets.timeout(), is(2L));

        time.set(2);
        int rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(tickets.timeout(), is(28L));

        time.set(29);
        rc = tickets.execute();
        assertThat(rc, is(0));

        time.set(30);
        rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(tickets.timeout(), is(10L));
    }

    @Test
    public void testAddFaultyHandler()
    {
        ZTicket.Ticket ticket = tickets.add(10, null);
        assertThat(ticket, nullValue());
    }

    @Test
    public void testCancelTwice()
    {
        ZTicket.Ticket ticket = tickets.add(10, handler);
        assertThat(ticket, notNullValue());

        boolean rc = ticket.cancel();
        assertThat(rc, is(true));

        rc = ticket.cancel();
        assertThat(rc, is(false));
    }

    @Test
    public void testNotInvokedInitial()
    {
        tickets.add(100, handler, invoked);
        //  Ticker should not have been invoked yet
        int rc = tickets.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testNotInvokedJustBefore()
    {
        tickets.add(100, handler, invoked);

        //  Wait just before expiration and check again
        time.set(99);
        int rc = tickets.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testInvoked()
    {
        long fullTimeout = 100;
        tickets.add(fullTimeout, handler, invoked);

        // Wait until the end
        time.set(fullTimeout);
        int rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(1));
    }

    @Test
    public void testInvoked2Times()
    {
        long fullTimeout = 100;
        tickets.add(fullTimeout, handler, invoked);
        tickets.add(fullTimeout, handler, invoked);

        // Wait until the end
        time.set(fullTimeout);
        int rc = tickets.execute();
        assertThat(rc, is(2));
        assertThat(invoked.get(), is(2));
    }

    @Test
    public void testNotInvokedAfter()
    {
        long fullTimeout = 50;
        tickets.add(fullTimeout, handler, invoked);

        // Wait until the end
        time.set(fullTimeout);
        int rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(1));

        //  Wait half the time and check again
        time.set(time.get() + fullTimeout);
        rc = tickets.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(1));
    }

    @Test
    public void testNotInvokedAfterResetHalfTime()
    {
        long fullTimeout = 50;
        ZTicket.Ticket ticket = tickets.add(fullTimeout, handler, invoked);

        //  Wait half the time and check again
        time.set(fullTimeout / 2);
        int rc = tickets.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(0));

        // Reset ticket and wait half of the time left
        ticket.reset();

        time.set(time.get() + fullTimeout / 2);
        rc = tickets.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(0));
    }

    @Test
    public void testInvokedAfterReset()
    {
        long fullTimeout = 50;
        testNotInvokedAfterResetHalfTime();

        // Wait until the end
        time.set(time.get() + fullTimeout);
        int rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(1));
    }

    @Test
    public void testReschedule()
    {
        long fullTimeout = 50;
        ZTicket.Ticket ticket = tickets.add(fullTimeout, handler, invoked);

        // reschedule
        ticket.setDelay(fullTimeout / 2);

        time.set(fullTimeout / 2);
        int rc = tickets.execute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(1));
    }

    @Test
    public void testCancelledTimerIsRemoved()
    {
        ArrayList<Ticket> list = new ArrayList<>();
        tickets = new ZTicket(time::get, list);

        ZTicket.Ticket ticket100 = tickets.add(100, handler, invoked);
        ZTicket.Ticket ticket1000 = tickets.add(1000, handler, invoked);
        ZTicket.Ticket ticket10 = tickets.add(10, handler, invoked);

        long timeout = tickets.timeout();
        // timeout sorted the tickets, by order of execution
        assertThat(timeout, is(10L));
        assertThat(list.get(0), is(ticket10));
        assertThat(list.get(1), is(ticket100));
        assertThat(list.get(2), is(ticket1000));

        ticket10.cancel();
        // cancel did not touch the order
        assertThat(list.get(0), is(ticket10));
        assertThat(list.get(1), is(ticket100));
        assertThat(list.get(2), is(ticket1000));

        timeout = tickets.timeout();
        // timeout resorted the tickets by order of execution
        assertThat(timeout, is(100L));
        assertThat(list.get(0), is(ticket100));
        assertThat(list.get(1), is(ticket1000));
        assertThat(list.get(2), is(ticket10));

        int rc = tickets.execute();
        // execute deleted the cancelled tickets
        assertThat(rc, is(0));
        assertThat(list.size(), is(2));
        assertThat(list.get(0), is(ticket100));
        assertThat(list.get(1), is(ticket1000));

        ticket10 = tickets.add(10, handler, invoked);
        assertThat(list.get(0), is(ticket100));
        assertThat(list.get(1), is(ticket1000));
        assertThat(list.get(2), is(ticket10));
        ticket10.cancel();

        rc = tickets.execute();
        // execute deleted the cancelled tickets
        assertThat(rc, is(0));
        assertThat(list.size(), is(2));
        assertThat(list.get(0), is(ticket100));
        assertThat(list.get(1), is(ticket1000));
    }

    @Test
    public void testCancel()
    {
        long fullTimeout = 50;
        ZTicket.Ticket ticket = tickets.add(fullTimeout, handler, invoked);

        // cancel ticket
        boolean ret = ticket.cancel();
        assertThat(ret, is(true));

        time.set(fullTimeout * 2);
        int rc = tickets.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(0));
    }

    @Test
    public void testExtraLongInsertions()
    {
        int max = 100_000;
        Random random = new Random();

        List<Integer> delays = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();
        for (int idx = 0; idx < max; ++idx) {
            delays.add(random.nextInt(1_000_000) + 1);
        }
        long start = System.currentTimeMillis();
        for (int idx = 0; idx < max; ++idx) {
            tickets.add(this.tickets.add(delays.get(idx), NOOP));
        }
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        System.out.printf("ZTicket Add: %s millisec spent on %s iterations: %s microsecs%n",
                      elapsed,
                      max,
                      1000 * elapsed / ((double) max));

        start = System.currentTimeMillis();
        long timeout = this.tickets.timeout();
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTicket Timeout: %s millisec %n", elapsed);

        this.time.set(this.time.get() + timeout);
        start = System.currentTimeMillis();
        int rc = this.tickets.execute();
        end = System.currentTimeMillis();
        elapsed = end - start;
        assertThat(rc > 0, is(true));
        System.out.printf("ZTicket Execute: %s millisec %n", elapsed);

        start = System.currentTimeMillis();
        for (Ticket t : tickets) {
            t.reset();
        }
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTicket Reset: %s millisec spent on %s iterations: %s microsecs%n",
                      elapsed,
                      max,
                      1000 * elapsed / ((double) max));

        start = System.currentTimeMillis();
        for (Ticket t : tickets) {
            t.cancel();
        }
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTicket Cancel: %s millisec spent on %s iterations: %s microsecs%n",
                      elapsed,
                      max,
                      1000 * elapsed / ((double) max));
    }
}
