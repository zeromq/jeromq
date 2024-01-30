package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZAuth.ZapRequest;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMonitor.Event;
import org.zeromq.ZMonitor.ZEvent;

import zmq.io.mechanism.curve.Curve;

public class ZMonitorTest
{
    @Test
    public void testZMonitorImpossibleWorkflows()
    {
        final ZContext ctx = new ZContext();
        final Socket socket = ctx.createSocket(SocketType.DEALER);

        final ZMonitor monitor = new ZMonitor(ctx, socket);

        // impossible to monitor events before being started
        ZEvent event = monitor.nextEvent();
        Assert.assertNull(event);
        event = monitor.nextEvent(-1);
        Assert.assertNull(event);

        monitor.start();

        // all no-ops commands once ZMonitor is started
        monitor.add().remove().verbose(false).start();

        socket.close();
        monitor.close();
        ctx.close();
    }

    @Test
    public void testZMonitor()
    {
        final ZContext ctx = new ZContext();
        final Socket client = ctx.createSocket(SocketType.DEALER);
        final Socket server = ctx.createSocket(SocketType.DEALER);

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
        Assert.assertEquals(Event.LISTENING, received.type);

        //  Check server connected to client
        boolean rc = client.connect("tcp://127.0.0.1:" + port);
        Assert.assertTrue(rc);
        received = clientMonitor.nextEvent();
        assertThat(received.type, is(Event.CONNECTED));

        //  Check server accepted connection
        received = serverMonitor.nextEvent(true);
        Assert.assertEquals(Event.ACCEPTED, received.type);

        //  Check server accepted connection
        received = serverMonitor.nextEvent(-1); // timeout -1 aka blocking
        Assert.assertEquals(Event.HANDSHAKE_PROTOCOL, received.type);

        received = serverMonitor.nextEvent(false); // with no blocking
        Assert.assertNull(received);

        received = serverMonitor.nextEvent(10); // timeout
        Assert.assertNull(received);

        client.close();
        clientMonitor.close();

        server.close();
        serverMonitor.close();

        ctx.close();
    }

    @Test(timeout = 5000)
    public void testZMonitorCurveOK()
    {
        final List<ZEvent> receivedEventsClient = new ArrayList<>();
        final List<ZEvent> receivedEventsServer = new ArrayList<>();
        final byte[][] serverKeyPair = new Curve().keypair();
        final byte[] serverPublicKey = serverKeyPair[0];
        final byte[] serverSecretKey = serverKeyPair[1];

        final byte[][] clientKeyPair = new Curve().keypair();
        final byte[] clientPublicKey = clientKeyPair[0];
        final byte[] clientSecretKey = clientKeyPair[1];

        final ZContext ctx = new ZContext();
        final Socket client = ctx.createSocket(SocketType.PUSH);
        client.setCurveServerKey(serverPublicKey);
        client.setCurvePublicKey(clientPublicKey);
        client.setCurveSecretKey(clientSecretKey);
        final Socket server = ctx.createSocket(SocketType.PULL);
        server.setCurveServer(true);
        server.setCurveSecretKey(serverSecretKey);

        final ZMonitor clientMonitor = new ZMonitor(ctx, client);
        clientMonitor.verbose(true);
        clientMonitor.add(Event.ALL);
        clientMonitor.start();

        final ZMonitor serverMonitor = new ZMonitor(ctx, server);
        serverMonitor.verbose(true);
        serverMonitor.add(Event.ALL);
        serverMonitor.start();

        //  Check server is now listening
        int port = server.bindToRandomPort("tcp://127.0.0.1");

        //  Check server connected to client
        boolean rc = client.connect("tcp://127.0.0.1:" + port);
        client.send("hello");
        server.recvStr();
        Assert.assertTrue(rc);
        client.close();

        ZEvent received;
        while ((received = clientMonitor.nextEvent(100)) != null) {
            receivedEventsClient.add(received);
        }
        clientMonitor.close();

        server.close();
        while ((received = serverMonitor.nextEvent(100)) != null) {
            receivedEventsServer.add(received);
        }
        serverMonitor.close();

        ctx.close();
        // [ZEvent [_PROTOCOL, code=32768, address=tcp://127.0.0.1:53682, value=3], ZEvent [type=DISCONNECTED, code=512, address=tcp://127.0.0.1:53682, value=null]]

        final Event[] expectedEventsClient = new Event[] {
            Event.CONNECT_DELAYED,
            Event.CONNECTED,
            Event.HANDSHAKE_PROTOCOL,
            Event.MONITOR_STOPPED,
        };
        check(receivedEventsClient, expectedEventsClient);
        final Event[] expectedEventsServer = new Event[] {
            Event.LISTENING,
            Event.ACCEPTED,
            Event.HANDSHAKE_PROTOCOL,
            Event.DISCONNECTED,
            Event.CLOSED,
            Event.MONITOR_STOPPED,
        };
        check(receivedEventsServer, expectedEventsServer);
    }

    @Test(timeout = 5000)
    public void testZMonitorCurveKo() throws InterruptedException
    {
        final List<ZEvent> receivedEventsClient = new ArrayList<>();
        final List<ZEvent> receivedEventsServer = new ArrayList<>();

        final byte[][] serverKeyPair = new Curve().keypair();
        final byte[] serverPublicKey = serverKeyPair[0];
        final byte[] serverSecretKey = serverKeyPair[1];

        final byte[][] clientKeyPair = new Curve().keypair();
        final byte[] clientPublicKey = clientKeyPair[0];
        final byte[] clientSecretKey = clientKeyPair[1];

        final ZContext ctx = new ZContext();
        final Socket client = ctx.createSocket(SocketType.PUSH);
        client.setCurveServerKey(serverPublicKey);
        client.setCurvePublicKey(clientPublicKey);
        client.setCurveSecretKey(serverSecretKey);
        final Socket server = ctx.createSocket(SocketType.PULL);
        server.setCurveServer(true);
        server.setCurveSecretKey(clientSecretKey);

        final ZMonitor clientMonitor = new ZMonitor(ctx, client);
        clientMonitor.verbose(true);
        clientMonitor.add(Event.ALL);
        clientMonitor.start();

        final ZMonitor serverMonitor = new ZMonitor(ctx, server);
        serverMonitor.verbose(true);
        serverMonitor.add(Event.ALL);
        serverMonitor.start();

        //  Check server is now listening
        server.bind("tcp://127.0.0.1:34782");

        //  Check server connected to client
        boolean rc = client.connect("tcp://127.0.0.1:" + 34782);
        Assert.assertTrue(rc);
        Thread.sleep(100);
        client.send("hello");
        Thread.sleep(100);
        client.close();

        ZEvent received;
        while ((received = clientMonitor.nextEvent(100)) != null) {
            receivedEventsClient.add(received);
        }
        clientMonitor.close();

        server.close();
        while ((received = serverMonitor.nextEvent(100)) != null) {
            receivedEventsServer.add(received);
        }
        serverMonitor.close();

        ctx.close();

        final Event[] expectedEventsClient = new Event[] {
            Event.CONNECT_DELAYED,
            Event.CONNECTED,
            Event.HANDSHAKE_PROTOCOL,
            Event.DISCONNECTED,
            Event.MONITOR_STOPPED,
        };
        check(receivedEventsClient, expectedEventsClient);
        final Event[] expectedEventsServer = new Event[] {
            Event.LISTENING,
            Event.ACCEPTED,
            Event.HANDSHAKE_PROTOCOL,
            Event.HANDSHAKE_FAILED_PROTOCOL,
            Event.DISCONNECTED,
            Event.CLOSED,
            Event.MONITOR_STOPPED,
        };
        check(receivedEventsServer, expectedEventsServer);
    }

    @Test(timeout = 5000)
    public void testPlainKo()
    {
        final List<ZEvent> receivedEventsClient = new ArrayList<>();
        final List<ZEvent> receivedEventsServer = new ArrayList<>();

        ZAuth.Auth plain = new ZAuth.Auth() {
            @Override
            public boolean authorize(ZapRequest request, boolean verbose)
            {
                return false;
            }
            @Override
            public boolean configure(ZMsg msg, boolean verbose)
            {
                return true;
            }
        };

        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, "authenticator", Collections.singletonMap("PLAIN", plain));
             Socket server = ctx.createSocket(SocketType.PUSH);
             ZMonitor serverMonitor = new ZMonitor(ctx, server);
             Socket client = ctx.createSocket(SocketType.PULL);
             ZMonitor clientMonitor = new ZMonitor(ctx, client)) {
            clientMonitor.verbose(true);
            clientMonitor.add(Event.ALL);
            clientMonitor.start();

            serverMonitor.verbose(true);
            serverMonitor.add(Event.ALL);
            serverMonitor.start();

            server.setPlainServer(true);
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(true);
            // auth send the replies
            auth.replies(true);

            //  Create and bind server socket
            server.setPlainServer(true);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("wrong".getBytes());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            Assert.assertTrue(rc);

            //  Send a single message from server to client
            rc = server.send("Hello");
            Assert.assertTrue(rc);

            ZAuth.ZapReply reply = auth.nextReply();
            Assert.assertEquals(400, reply.statusCode);

            ZEvent received;
            while ((received = clientMonitor.nextEvent(100)) != null) {
                receivedEventsClient.add(received);
            }

            while ((received = serverMonitor.nextEvent(100)) != null) {
                receivedEventsServer.add(received);
            }

            final Event[] expectedEventsClient = new Event[] {
                Event.CONNECT_DELAYED,
                Event.CONNECTED,
                Event.HANDSHAKE_PROTOCOL,
                Event.HANDSHAKE_FAILED_AUTH,
                Event.DISCONNECTED,
           };
            check(receivedEventsClient, expectedEventsClient);
            final Event[] expectedEventsServer = new Event[] {
                Event.LISTENING,
                Event.ACCEPTED,
                Event.HANDSHAKE_PROTOCOL,
                Event.DISCONNECTED,
            };
            check(receivedEventsServer, expectedEventsServer);
        }
    }

    private void check(final List<ZEvent> receivedEvents, final Event[] expectedEvents)
    {
        Assert.assertEquals(expectedEvents.length, receivedEvents.size());
        for (int i = 0; i < expectedEvents.length; i++) {
            Assert.assertEquals(expectedEvents[i], receivedEvents.get(i).type);
        }
    }
}
