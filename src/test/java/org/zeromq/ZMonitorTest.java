package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMonitor.ZEvent;
import org.zeromq.ZMonitor.Event;

public class ZMonitorTest
{
    @Test
    public void testZMonitor()
    {
        final ZContext ctx = new ZContext();
        final Socket client = ctx.createSocket(ZMQ.DEALER);
        final Socket server = ctx.createSocket(ZMQ.DEALER);

        final ZMonitor clientMonitor = new ZMonitor(ctx, client);
        clientMonitor.verbose(true);
        clientMonitor.addEvents(Event.LISTENING, Event.ACCEPTED);
        clientMonitor.start();

        final ZMonitor serverMonitor = new ZMonitor(ctx, server);
        serverMonitor.verbose(false);
        serverMonitor.addEvents(Event.LISTENING, Event.CONNECTED, Event.DISCONNECTED);
        serverMonitor.start();

        //  Allow a brief time for the message to get there...
        zmq.ZMQ.msleep(200);

        //  Check client is now listening
        int port = client.bindToRandomPort("tcp://127.0.0.1");
        ZEvent event = clientMonitor.nextEvent();
        assertThat(event.type, is(Event.LISTENING));

        //  Check server connected to client
        server.connect("tcp://127.0.0.1:" + port);
        event = serverMonitor.nextEvent();
        assertThat(event.type, is(Event.CONNECTED));

        //  Check client accepted connection
        event = clientMonitor.nextEvent(true);
        assertThat(event.type, is(Event.ACCEPTED));

        client.close();
        server.close();

        clientMonitor.close();
        serverMonitor.close();
        ctx.close();
    }
}
