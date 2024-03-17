package org.zeromq.timer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.zeromq.timer.ZTicket.Ticket;
import org.zeromq.timer.ZTimer.Timer;

import zmq.ZMQ;

public class ZTickerTest
{
    private ZTicker ticker;

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
        ticker = new ZTicker();
    }

    @Test
    public void testNoTicket()
    {
        assertThat(ticker.timeout(), is(-1L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTicketInsertion()
    {
        ticker.addTicket(-1, NOOP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTimerInsertion()
    {
        ticker.addTimer(-1, NOOP);
    }

    @Test
    public void testTicketInsertion()
    {
        ZTicker ticker = new ZTicker(() -> 0L);
        Ticket ticket = ticker.addTicket(1, NOOP);
        assertThat(ticket, is(notNullValue()));
        assertThat(ticker.timeout(), is(1L));
    }

    @Test
    public void testTicketTimeout()
    {
        ZTicker ticker = new ZTicker();
        Ticket ticket = ticker.addTicket(1, NOOP);
        ZMQ.sleep(1);
        assertThat(ticket, is(notNullValue()));
        assertThat(ticker.timeout(), is(0L));
    }

    @Test
    public void testTimerInsertion()
    {
        ZTicker ticker = new ZTicker(() -> 0L);
        Timer timer = ticker.addTimer(1, NOOP);
        assertThat(timer, is(notNullValue()));
        assertThat(ticker.timeout(), is(1L));
    }

    @Test
    public void testTimerAndTicketInsertion()
    {
        ZTicker ticker = new ZTicker(() -> 10L);

        Ticket ticket = ticker.addTicket(1, NOOP);
        assertThat(ticket, is(notNullValue()));
        Timer timer = ticker.addTimer(2, NOOP);
        assertThat(timer, is(notNullValue()));

        assertThat(ticker.timeout(), is(1L));
    }

    @Test
    public void testExecution()
    {
        AtomicLong time = new AtomicLong();
        ZTicker ticker = new ZTicker(time::get);

        AtomicInteger timerTriggered = new AtomicInteger();
        AtomicInteger ticketTriggered = new AtomicInteger();

        Timer timer = ticker.addTimer(10, handler, timerTriggered);
        assertThat(timer, is(notNullValue()));
        Ticket ticket = ticker.addTicket(30, handler, ticketTriggered);
        assertThat(ticket, is(notNullValue()));

        long timeout = ticker.timeout();
        assertThat(timeout, is(10L));
        time.set(time.get() + timeout);
        int executed = ticker.execute();
        assertThat(executed, is(1));
        assertThat(timerTriggered.get(), is(1));
        assertThat(ticketTriggered.get(), is(0));

        timeout = ticker.timeout();
        assertThat(timeout, is(10L));
        time.set(time.get() + timeout);
        executed = ticker.execute();
        assertThat(executed, is(1));
        assertThat(timerTriggered.get(), is(2));
        assertThat(ticketTriggered.get(), is(0));

        timeout = ticker.timeout();
        assertThat(timeout, is(10L));
        time.set(time.get() + timeout);
        executed = ticker.execute();
        assertThat(executed, is(2));
        assertThat(timerTriggered.get(), is(3));
        assertThat(ticketTriggered.get(), is(1));

        timeout = ticker.timeout();
        assertThat(timeout, is(10L));
        time.set(time.get() + timeout);
        executed = ticker.execute();
        assertThat(executed, is(1));
        assertThat(timerTriggered.get(), is(4));
        assertThat(ticketTriggered.get(), is(1));
    }
}
