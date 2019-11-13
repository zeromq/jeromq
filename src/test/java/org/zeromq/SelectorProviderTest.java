package org.zeromq;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

import zmq.Options;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.SelectorProviderChooser;

public class SelectorProviderTest
{
    private static final class SelectorProviderCustom extends SelectorProvider
    {
        private final AtomicInteger chosen;

        SelectorProviderCustom(AtomicInteger chosen)
        {
            this.chosen = chosen;
        }

        @Override
        public DatagramChannel openDatagramChannel() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DatagramChannel openDatagramChannel(ProtocolFamily protocolFamily) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Pipe openPipe() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public AbstractSelector openSelector() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerSocketChannel openServerSocketChannel() throws IOException
        {
            chosen.addAndGet(1);
            return ServerSocketChannel.open();
        }

        @Override
        public SocketChannel openSocketChannel() throws IOException
        {
            chosen.addAndGet(1);
            return SocketChannel.open();
        }
    }

    public static class DefaultSelectorProviderChooser implements SelectorProviderChooser
    {
        private AtomicInteger chosen = new AtomicInteger(0);

        @Override
        public SelectorProvider choose(IZAddress addr, Options options)
        {
            return new SelectorProviderCustom(chosen);
        }
    }

    @Test
    public void test()
    {
        try (
             ZContext ctx = new ZContext();
             Socket pull = ctx.createSocket(SocketType.PULL);
             Socket push = ctx.createSocket(SocketType.PUSH)) {
            DefaultSelectorProviderChooser chooser = new DefaultSelectorProviderChooser();
            pull.setSelectorChooser(chooser);
            push.setSelectorChooser(chooser);
            pull.bind("tcp://*:*");
            push.connect(pull.getLastEndpoint());

            String expected = "hello";
            push.send(expected);
            String actual = new String(pull.recv());

            assertEquals(expected, actual);
            // Ensure that the choose method was indeed called for each socket.
            assertEquals(2, chooser.chosen.get());
        }
    }
}
