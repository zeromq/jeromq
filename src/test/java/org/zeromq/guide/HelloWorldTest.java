package org.zeromq.guide;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.Utils;
import org.zeromq.ZActor;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller;

public class HelloWorldTest
{
    private static final class Client extends ZActor.SimpleActor
    {
        private final int port;

        private int requestNumber = 0;

        public Client(int port)
        {
            this.port = port;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket req = ctx.createSocket(SocketType.REQ);
            assertThat(req, notNullValue());
            return Collections.singletonList(req);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket req = sockets.get(0);
            boolean rc = req.connect("tcp://*:" + port);
            assertThat(rc, is(true));
            rc = poller.register(req, ZPoller.OUT);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            String request = "Hello " + ++requestNumber;
            System.out.println("C - Sending Hello " + requestNumber);
            socket.send(request.getBytes(ZMQ.CHARSET), 0);

            byte[] reply = socket.recv(0);
            System.out.println("C - Received " + new String(reply, ZMQ.CHARSET) + " " + requestNumber);

            return requestNumber < 10;
        }
    }

    private static final class Server extends ZActor.SimpleActor
    {
        private final int port;

        public Server(int port)
        {
            this.port = port;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket rep = ctx.createSocket(SocketType.REP);
            assertThat(rep, notNullValue());
            return Collections.singletonList(rep);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket rep = sockets.get(0);
            boolean rc = rep.bind("tcp://*:" + port);
            assertThat(rc, is(true));
            rc = poller.register(rep, ZPoller.IN);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            byte[] reply = socket.recv(0);
            System.out.println("S - Received " + ": [" + new String(reply, ZMQ.CHARSET) + "]");

            ZMQ.msleep(100);

            String response = "world";
            return socket.send(response.getBytes(ZMQ.CHARSET), 0);
        }
    }

    @Test
    public void testHelloWorld() throws IOException
    {
        final int port = Utils.findOpenPort();

        try (
             final ZContext ctx = new ZContext()) {
            ZActor server = new ZActor(ctx, new Server(port), "motdelafin");
            ZActor client = new ZActor(ctx, new Client(port), "motdelafin");

            client.exit().awaitSilent();

            boolean rc = server.send("anything-sent-will-end-the-actor");
            assertThat(rc, is(true));

            server.exit().awaitSilent();
            System.out.println("Hello World Finished");
        }
    }
}
