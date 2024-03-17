package org.zeromq;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TestSocketStreams
{
    @Test
    public void testRecvStream() throws IOException
    {
        int port = Utils.findOpenPort();
        try (
                final ZMQ.Context ctx = new ZMQ.Context(1);
                final ZMQ.Socket pull = ctx.socket(SocketType.PULL);
                final ZMQ.Socket push = ctx.socket(SocketType.PUSH)) {
            pull.bind("tcp://*:" + port);
            push.connect("tcp://127.0.0.1:" + port);

            final byte[] expected = new byte[]{0x11, 0x22, 0x33};
            push.send(expected);
        }
    }

    @Test
    public void testRecvStrStream() throws IOException
    {
        int port = Utils.findOpenPort();
        try (
                final ZMQ.Context ctx = new ZMQ.Context(1);
                final ZMQ.Socket pull = ctx.socket(SocketType.PULL);
                final ZMQ.Socket push = ctx.socket(SocketType.PUSH)) {
            pull.bind("tcp://*:" + port);
            push.connect("tcp://127.0.0.1:" + port);

            final String expected = "Hello";
            push.send(expected);
//            Optional<String> first = pull.recvStrStream().peek(System.out::print).findFirst();
//            assertTrue(first.isPresent());
//            assertEquals(expected, first.get());
        }
    }
}
