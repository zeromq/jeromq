package org.zeromq.guide;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.Utils;
import org.zeromq.ZActor;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;
import org.zeromq.ZPoller;
import org.zeromq.ZProxy;
import org.zeromq.ZProxy.Plug;
import org.zeromq.timer.TimerHandler;
import org.zeromq.timer.ZTimer;

public class AsyncServerTest
{
    private static final Random        rand    = new Random(System.nanoTime());
    private static final AtomicInteger counter = new AtomicInteger();

    //---------------------------------------------------------------------
    //This is our client task
    //It connects to the server, and then sends a request once per second
    //It collects responses as they arrive, and it prints them out. We will
    //run several client tasks in parallel, each with a different random ID.
    private static class Client extends ZActor.SimpleActor implements TimerHandler
    {
        private int          requestNbr = 0;
        private final String identity   = String.format("%04X-%04X", counter.incrementAndGet(), rand.nextInt());
        private final ZTimer timer      = new ZTimer();
        private ZTimer.Timer handle;
        private Socket       client;
        private final int    port;

        public Client(int port)
        {
            this.port = port;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            client = ctx.createSocket(SocketType.DEALER);
            assertThat(client, notNullValue());
            return Collections.singletonList(client);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            boolean rc = client.setIdentity(identity.getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));
            rc = client.connect("tcp://localhost:" + port);
            assertThat(rc, is(true));
            rc = poller.register(client, ZPoller.POLLIN);
            assertThat(rc, is(true));
            handle = timer.add(100, this);
            assertThat(handle, notNullValue());
        }

        @Override
        public long looping(Socket pipe, ZPoller poller)
        {
            return timer.timeout();
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            ZMsg msg = ZMsg.recvMsg(socket);
            assertThat(msg, notNullValue());
            ZFrame content = msg.pop();
            assertThat(content, notNullValue());
            System.out.println(identity + " " + content);
            assertThat(content.getString(ZMQ.CHARSET), endsWith(identity));
            return true;
        }

        @Override
        public boolean looped(Socket pipe, ZPoller poller)
        {
            int rc = timer.sleepAndExecute();
            assertThat(rc, is(1));
            return super.looped(pipe, poller);
        }

        @Override
        public void time(Object... args)
        {
            boolean rc = client.send(String.format("request #%02d - %s", ++requestNbr, identity));
            assertThat(rc, is(true));
        }

        @Override
        public boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller)
        {
            boolean rc = handle.cancel();
            assertThat(rc, is(true));
            return super.destroyed(ctx, pipe, poller);
        }
    }

    //This is our server task.
    //It uses the multithreaded server model to deal requests out to a pool
    //of workers and route replies back to clients. One worker can handle
    //one request at a time but one client can talk to multiple workers at
    //once.
    private static class Proxy extends ZProxy.Proxy.SimpleProxy
    {
        private final int frontend;

        public Proxy(int frontend)
        {
            this.frontend = frontend;
        }

        @Override
        public Socket create(ZContext ctx, Plug place, Object... args)
        {
            switch (place) {
            case FRONT:
                Socket front = ctx.createSocket(SocketType.ROUTER);
                assertThat(front, notNullValue());
                return front;
            case BACK:
                Socket back = ctx.createSocket(SocketType.DEALER);
                assertThat(back, notNullValue());
                return back;
            default:
                return null;
            }
        }

        @Override
        public boolean configure(Socket socket, Plug place, Object... args)
        {
            switch (place) {
            case FRONT:
                return socket.bind("tcp://*:" + frontend);
            case BACK:
                return socket.bind("inproc://backend");
            default:
                return true;
            }
        }
    }

    //Each worker task works on one request at a time and sends a random number
    //of replies back, with random delays between replies:
    private static class Worker extends ZActor.SimpleActor
    {
        private int       counter = 0;
        private final int id;

        public Worker(int id)
        {
            this.id = id;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket socket = ctx.createSocket(SocketType.DEALER);
            assertThat(socket, notNullValue());
            return Collections.singletonList(socket);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket worker = sockets.get(0);
            boolean rc = worker.setLinger(0);
            assertThat(rc, is(true));
            rc = worker.setReceiveTimeOut(100);
            assertThat(rc, is(true));
            rc = worker.setSendTimeOut(100);
            assertThat(rc, is(true));
            rc = worker.connect("inproc://backend");
            assertThat(rc, is(true));
            rc = poller.register(worker, ZPoller.IN);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket worker, Socket pipe, ZPoller poller, int events)
        {
            ZMsg msg = ZMsg.recvMsg(worker);
            if (msg == null) {
                return false;
            }
            ZFrame address = msg.pop();
            assertThat(address, notNullValue());
            String request = msg.popString();
            assertThat(request, notNullValue());
            //  Send 0..4 replies back
            final int replies = rand.nextInt(5);
            for (int reply = 0; reply < replies; reply++) {
                //  Sleep for some fraction of a second
                ZMQ.msleep(rand.nextInt(1000) + 1);
                msg = new ZMsg();
                boolean rc = msg.add(address);
                assertThat(rc, is(true));
                msg.add(String.format("worker #%s reply #%02d : %s", id, ++counter, request));
                rc = msg.send(worker);
                if (!rc) {
                    return false;
                }
            }
            return true;
        }
    }

    //The main thread simply starts several clients, and a server, and then
    //waits for the server to finish.
    @Test(timeout = 10000)
    public void testAsyncServer() throws Exception
    {
        try (
             final ZContext ctx = new ZContext()) {
            final int port = Utils.findOpenPort();
            List<ZActor> actors = new ArrayList<>();

            actors.add(new ZActor(new Client(port), null));
            actors.add(new ZActor(new Client(port), null));
            actors.add(new ZActor(new Client(port), null));

            ZProxy proxy = ZProxy.newProxy(ctx, "proxy", new Proxy(port), null);
            String status = proxy.start(true);
            assertThat(status, is(ZProxy.STARTED));
            //  Launch pool of worker threads, precise number is not critical
            for (int threadNbr = 0; threadNbr < 5; threadNbr++) {
                actors.add(new ZActor(ctx, new Worker(threadNbr), null));
            }

            ZMQ.sleep(1);

            status = proxy.pause(true);
            assertThat(status, is(ZProxy.PAUSED));

            for (ZActor actor : actors) {
                boolean rc = actor.send("fini");
                assertThat(rc, is(true));
                actor.exit().awaitSilent();
            }
            status = proxy.exit();
            assertThat(status, is(ZProxy.EXITED));
        }
    }
}
