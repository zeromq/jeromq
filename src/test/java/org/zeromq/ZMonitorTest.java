package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMonitor.Event;
import org.zeromq.ZMonitor.ZEvent;

public class ZMonitorTest
{
    @Test
    public void testZMonitorImpossibleWorkflows() throws IOException
    {
        final ZContext ctx = new ZContext();
        final Socket socket = ctx.createSocket(ZMQ.DEALER);

        final ZMonitor monitor = new ZMonitor(ctx, socket);

        // impossible to monitor events before being started
        ZEvent event = monitor.nextEvent();
        assertThat(event, nullValue());
        event = monitor.nextEvent(-1);
        assertThat(event, nullValue());

        monitor.start();

        // all no-ops commands once ZMonitor is started
        monitor.add().remove().verbose(false).start();

        socket.close();
        monitor.close();
        ctx.close();
    }

    @Test
    public void testZMonitor() throws IOException
    {
        final ZContext ctx = new ZContext();
        final Socket client = ctx.createSocket(ZMQ.DEALER);
        final Socket server = ctx.createSocket(ZMQ.DEALER);

        final ZMonitor clientMonitor = new ZMonitor(ctx, client);
        clientMonitor.verbose(true);
        clientMonitor.add(Event.LISTENING, Event.CONNECTED, Event.DISCONNECTED, Event.ACCEPT_FAILED);
        clientMonitor.remove(Event.ACCEPT_FAILED);

        clientMonitor.start();

        final ZMonitor serverMonitor = new ZMonitor(ctx, server);
        serverMonitor.verbose(false);
        serverMonitor.add(Event.LISTENING, Event.ACCEPTED, Event.HANDSHAKE_PROTOCOL);
        serverMonitor.start();

        //  Check server is now listening
        int port = server.bindToRandomPort("tcp://127.0.0.1");
        ZEvent received = serverMonitor.nextEvent();
        assertThat(received.type, is(Event.LISTENING));

        //  Check server connected to client
        boolean rc = client.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));
        received = clientMonitor.nextEvent();
        assertThat(received.type, is(Event.CONNECTED));

        //  Check server accepted connection
        received = serverMonitor.nextEvent(true);
        assertThat(received.type, is(Event.ACCEPTED));

        System.out.println("server @ tcp://127.0.0.1:" + port + " received " + received.toString());

        //  Check server accepted connection
        received = serverMonitor.nextEvent(-1); // timeout -1 aka blocking
        assertThat(received.type, is(Event.HANDSHAKE_PROTOCOL));

        received = serverMonitor.nextEvent(false); // with no blocking
        assertThat(received, nullValue());

        received = serverMonitor.nextEvent(10); // timeout
        assertThat(received, nullValue());

        client.close();
        clientMonitor.close();

        server.close();
        serverMonitor.close();

        ctx.close();
    }

    //    @Test
    public void testRepeated() throws IOException
    {
        for (int idx = 0; idx < 10000; ++idx) {
            System.out.println("+++++ " + idx);
            testZMonitor();
        }
    }
}
