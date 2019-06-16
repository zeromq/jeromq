package org.zeromq;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class ZSocketTest
{
    @Test
    public void pushPullTest() throws IOException
    {
        int port = Utils.findOpenPort();

        try (
             final ZSocket pull = new ZSocket(SocketType.PULL);
             final ZSocket push = new ZSocket(SocketType.PUSH)) {
            pull.bind("tcp://*:" + port);
            push.connect("tcp://127.0.0.1:" + port);

            final String expected = "hello";
            push.sendStringUtf8(expected);
            final String actual = pull.receiveStringUtf8();

            assertEquals(expected, actual);
            assertEquals(SocketType.PULL, pull.getSocketType());
            assertEquals(ZMQ.PULL, pull.getType());
            assertEquals(SocketType.PUSH, push.getSocketType());
            assertEquals(ZMQ.PUSH, push.getType());
        }
    }
}
