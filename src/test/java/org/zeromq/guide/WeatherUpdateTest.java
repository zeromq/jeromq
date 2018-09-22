package org.zeromq.guide;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.Utils;
import org.zeromq.ZActor;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller;
import org.zeromq.ZProxy;
import org.zeromq.ZProxy.Plug;

public class WeatherUpdateTest
{
    private static final class Client extends ZActor.SimpleActor
    {
        private final int    port;
        private final String filter;

        private int  requestNumber    = 0;
        private long totalTemperature = 0;

        public Client(int port, String filter)
        {
            this.port = port;
            this.filter = filter;
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
            Socket sub = sockets.get(0);
            boolean rc = sub.connect("tcp://*:" + port);
            assertThat(rc, is(true));

            rc = sub.subscribe(filter.getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));

            rc = poller.register(sub, ZPoller.IN);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket socket, Socket pipe, ZPoller poller, int events)
        {
            //  Use trim to remove the tailing '0' character
            String string = socket.recvStr().trim();

            StringTokenizer sscanf = new StringTokenizer(string, " ");
            int zipcode = Integer.valueOf(sscanf.nextToken());
            int temperature = Integer.valueOf(sscanf.nextToken());
            int relhumidity = Integer.valueOf(sscanf.nextToken());

            totalTemperature += temperature;

            return requestNumber++ < 100;
        }

        @Override
        public boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller)
        {
            System.out.println(String.format(
                                             "Average temperature for zipcode '%s' was %d.",
                                             filter,
                                             (int) (totalTemperature / requestNumber)));
            return super.destroyed(ctx, pipe, poller);
        }
    }

    private static final class Server extends ZActor.SimpleActor
    {
        private final int port;

        private int updateNumber = 0;

        public Server(int port)
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
            Socket pub = sockets.get(0);
            boolean rc = pub.bind("tcp://*:" + port);
            assertThat(rc, is(true));

            rc = pub.bind("inproc://weather");
            assertThat(rc, is(true));

            rc = poller.register(pub, ZPoller.OUT);
            assertThat(rc, is(true));
        }

        @Override
        public boolean stage(Socket publisher, Socket pipe, ZPoller poller, int events)
        {
            ++updateNumber;

            Random srandom = new Random(System.currentTimeMillis());
            //  Get values that will fool the boss
            int zipcode, temperature, relhumidity;
            zipcode = 10000 + srandom.nextInt(10000);
            temperature = srandom.nextInt(215) - 80 + 1;
            relhumidity = srandom.nextInt(50) + 10 + 1;

            //  Send message to all subscribers
            String update = String.format("%05d %d %d", zipcode, temperature, relhumidity);
            return publisher.send(update, 0);
        }

        @Override
        public boolean destroyed(ZContext ctx, Socket pipe, ZPoller poller)
        {
            System.out.println(String.format("Sent %d weather updates", updateNumber));
            return super.destroyed(ctx, pipe, poller);
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
                return ctx.createSocket(SocketType.SUB);
            case BACK:
                return ctx.createSocket(SocketType.PUB);
            default:
                return null;
            }
        }

        @Override
        public boolean configure(Socket socket, Plug place, Object... args) throws IOException
        {
            switch (place) {
            case FRONT:
                socket.subscribe(ZMQ.SUBSCRIPTION_ALL);
                return socket.connect("tcp://localhost:" + frontend);
            case BACK:
                return socket.bind("tcp://*:" + backend);
            default:
                return true;
            }
        }
    }

    @Test
    public void testWeatherUpdate() throws IOException
    {
        final int frontend = Utils.findOpenPort();
        final int backend = Utils.findOpenPort();

        try (
             final ZContext ctx = new ZContext()) {

            ZActor server = new ZActor(ctx, new Server(frontend), "motdelafin");
            ZActor client = new ZActor(ctx, new Client(backend, "10001 "), "motdelafin");

            ZProxy proxy = ZProxy.newProxy(ctx, "proxy", new Proxy(frontend, backend), "motdelafin", "random", "args");
            String status = proxy.start(true);
            assertThat(status, is(ZProxy.STARTED));

            client.exit().awaitSilent();

            boolean rc = server.send("anything-sent-will-end-the-actor");
            assertThat(rc, is(true));

            status = proxy.exit();
            assertThat(status, is(ZProxy.EXITED));

            server.exit().awaitSilent();
            System.out.println("Weather update Finished");
        }
    }
}
