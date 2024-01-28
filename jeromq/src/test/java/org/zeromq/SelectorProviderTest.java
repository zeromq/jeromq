package org.zeromq;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

import zmq.Options;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.SelectorProviderChooser;
import zmq.util.Utils;

public class SelectorProviderTest
{
    public static class DefaultSelectorProviderChooser implements SelectorProviderChooser
    {
        public final AtomicInteger choosen = new AtomicInteger(0);
        @Override
        public SelectorProvider choose(IZAddress addr, Options options)
        {
            choosen.addAndGet(1);
            return SelectorProvider.provider();
        }
    }

    @Test
    public void test() throws IOException
    {
        int port = Utils.findOpenPort();

        try (
             ZContext ctx = new ZContext();
             Socket pull = ctx.createSocket(SocketType.PULL);
             Socket push = ctx.createSocket(SocketType.PUSH)) {
            DefaultSelectorProviderChooser chooser = new DefaultSelectorProviderChooser();
            pull.setSelectorChooser(chooser);
            push.setSelectorChooser(chooser);
            pull.bind("tcp://*:" + port);
            push.connect("tcp://127.0.0.1:" + port);

            String expected = "hello";
            push.send(expected);
            String actual = new String(pull.recv());

            assertEquals(expected, actual);
            // Ensure that the choose method was indeed called for each socket.
            assertEquals(2, chooser.choosen.get());
        }
    }
}
