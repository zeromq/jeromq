package org.zeromq;

import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Isa Hekmatizadeh
 */
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
            Optional<byte[]> first = pull.recvStream().peek(System.out::print).findFirst();
            System.out.println();
            assertTrue(first.isPresent());
            assertArrayEquals(expected, first.get());
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
            Optional<String> first = pull.recvStrStream().peek(System.out::print).findFirst();
            System.out.println();
            assertTrue(first.isPresent());
            assertEquals(expected, first.get());
        }
    }
}
