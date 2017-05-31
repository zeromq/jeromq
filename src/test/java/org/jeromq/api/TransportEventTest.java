package org.jeromq.api;

import org.junit.Test;

import java.util.EnumSet;

import static junit.framework.Assert.assertEquals;

public class TransportEventTest {

    @Test
    public void testDecode_noValues() throws Exception {
        EnumSet<TransportEvent> result = TransportEvent.decode(0);
        assertEquals(EnumSet.noneOf(TransportEvent.class), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecode_junk() throws Exception {
        EnumSet<TransportEvent> result = TransportEvent.decode(2838383);
        System.out.println("result = " + result);
    }

    @Test
    public void testDecode() throws Exception {
        EnumSet<TransportEvent> result = TransportEvent.decode(TransportEvent.ZMQ_EVENT_ACCEPTED.getCValue() | TransportEvent.ZMQ_EVENT_CLOSED.getCValue());
        assertEquals(EnumSet.of(TransportEvent.ZMQ_EVENT_ACCEPTED, TransportEvent.ZMQ_EVENT_CLOSED), result);
    }
}
