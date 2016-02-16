package org.zeromq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ZSocketTest
{
    @Test
    public void pushPullTest()
    {
        try (final ZSocket pull = new ZSocket(ZMQ.PULL);
             final ZSocket push = new ZSocket(ZMQ.PUSH)) {
            pull.bind("tcp://*:7210");
            push.connect("tcp://127.0.0.1:7210");

            final String expected = "hello";
            push.sendStringUtf8(expected);
            final String actual = pull.receiveStringUtf8();

            assertEquals(expected, actual);
        }
    }
}
