package org.zeromq.guide;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.Utils;
import org.zeromq.ZActor;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller;
import org.zeromq.ZProxy;
import org.zeromq.ZProxy.Plug;

//  Espresso Pattern
//  This shows how to capture data using a pub-sub proxy
public class EspressoTest
{
    //  The subscriber thread requests messages starting with
    //  A and B, then reads and counts incoming messages.
    private static class Subscriber extends ZActor.SimpleActor
    {
        private final int port;
        private int       count;

        public Subscriber(int port)
        {
            this.port = port;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket sub = ctx.createSocket(SocketType.SUB);
            assertThat(sub, notNullValue());
            return Collections.singletonList(sub);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket subscriber = sockets.get(0);
            boolean rc = subscriber.connect("tcp://localhost:" + port);
            assertThat(rc, is(true));
            rc = subscriber.subscribe("A");
            assertThat(rc, is(true));
            rc = subscriber.subscribe("B".getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));
            rc = poller.register(subscriber, ZPoller.IN);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            String string = socket.recvStr();
            return string == null || count++ < 5;
        }
    }

    //  .split publisher thread
    //  The publisher sends random messages starting with A-J:
    private static class Publisher extends ZActor.SimpleActor
    {
        private final Random rand = new Random(System.currentTimeMillis());
        private final int    port;
        private int          count;

        public Publisher(int port)
        {
            this.port = port;
        }

        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket pub = ctx.createSocket(SocketType.PUB);
            assertThat(pub, notNullValue());
            return Collections.singletonList(pub);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket publisher = sockets.get(0);
            boolean rc = publisher.bind("tcp://*:" + port);
            assertThat(rc, is(true));
            rc = poller.register(publisher, ZPoller.OUT);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            ZMQ.msleep(100);
            String string = String.format("%c-%05d", 'A' + rand.nextInt(10), ++count);
            return socket.send(string);
        }
    }

    //  .split listener thread
    //  The listener receives all messages flowing through the proxy, on its
    //  pipe. In CZMQ, the pipe is a pair of ZMQ_PAIR sockets that connect
    //  attached child threads. In other languages your mileage may vary:
    private static class Listener extends ZActor.SimpleActor
    {
        @Override
        public List<Socket> createSockets(ZContext ctx, Object... args)
        {
            Socket pull = ctx.createSocket(SocketType.PULL);
            assertThat(pull, notNullValue());
            return Collections.singletonList(pull);
        }

        @Override
        public void start(Socket pipe, List<Socket> sockets, ZPoller poller)
        {
            Socket subscriber = sockets.get(0);
            boolean rc = subscriber.connect("inproc://captured");
            assertThat(rc, is(true));
            rc = poller.register(subscriber, ZPoller.IN);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            ZFrame frame = ZFrame.recvFrame(socket);
            assertThat(frame, notNullValue());
            frame.print(null);
            frame.destroy();
            return true;
        }
    }

    private static class Proxy extends ZProxy.Proxy.SimpleProxy
    {
        private final int frontend;
        private final int backend;

        public Proxy(int frontend, int backend)
        {
            this.frontend = frontend;
            this.backend = backend;
        }

        @Override
        public Socket create(ZContext ctx, Plug place, Object... args)
        {
            switch (place) {
            case FRONT:
                return ctx.createSocket(SocketType.XSUB);
            case BACK:
                return ctx.createSocket(SocketType.XPUB);
            case CAPTURE:
                return ctx.createSocket(SocketType.PUSH);
            default:
                return null;
            }
        }

        @Override
        public boolean configure(Socket socket, Plug place, Object... args) throws IOException
        {
            switch (place) {
            case FRONT:
                return socket.connect("tcp://localhost:" + frontend);
            case BACK:
                return socket.bind("tcp://*:" + backend);
            case CAPTURE:
                return socket.bind("inproc://captured");
            default:
                return true;
            }
        }
    }

    //  .split main thread
    //  The main task starts the subscriber and publisher, and then sets
    //  itself up as a listening proxy. The listener runs as a child thread:
    @Test
    public void testEspresso() throws IOException
    {
        final int frontend = Utils.findOpenPort();
        final int backend = Utils.findOpenPort();
        try (
             final ZContext ctx = new ZContext()) {
            ZActor publisher = new ZActor(ctx, new Publisher(frontend), "motdelafin");
            ZActor subscriber = new ZActor(ctx, new Subscriber(backend), "motdelafin");
            ZActor listener = new ZActor(ctx, new Listener(), "motdelafin");

            ZProxy proxy = ZProxy.newZProxy(ctx, "Proxy", new Proxy(frontend, backend), "motdelafin");
            String status = proxy.start(true);
            assertThat(status, is(ZProxy.STARTED));

            ZMQ.sleep(10);

            boolean rc = publisher.send("anything-sent-will-end-the-actor");
            assertThat(rc, is(true));
            // subscriber is already stopped after 5 receptions
            rc = listener.send("Did I really say ANYTHING?");
            assertThat(rc, is(true));

            status = proxy.exit();
            assertThat(status, is(ZProxy.EXITED));

            publisher.exit().awaitSilent();
            subscriber.exit().awaitSilent();
            listener.exit().awaitSilent();
            System.out.println("Espresso Finished");
        }
    }
}
