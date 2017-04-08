package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class TestProxy
{
    static class Client extends Thread
    {
        private final int            port;
        private final String         name;
        private final AtomicBoolean  result = new AtomicBoolean();
        private final CountDownLatch latch;

        public Client(String name, int port, CountDownLatch latch)
        {
            this.latch = latch;

            this.name = name;
            this.port = port;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assertThat(ctx, notNullValue());

            Socket socket = ctx.socket(ZMQ.REQ);
            boolean rc;
//            rc = socket.setImmediate(false);
//            assertThat(rc, is(true));
            rc = socket.setIdentity(name.getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));

            System.out.println("Start " + name);
            setName(name);

            rc = socket.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            result.set(process(socket));
            latch.countDown();
            socket.close();
            ctx.close();
            System.out.println("Stop " + name);
        }

        private boolean process(Socket socket)
        {
            boolean rc = socket.send("hello");
            if (!rc) {
                System.out.println(name + " unable to send first message");
                return false;
            }
            System.out.println(name + " sent 1st message");
            String msg = socket.recvStr(0);
            System.out.println(name + " received " + msg);
            if (msg == null || !msg.startsWith("OK hello")) {
                return false;
            }
            rc = socket.send("world");
            if (!rc) {
                System.out.println(name + " unable to send second message");
                return false;
            }
            msg = socket.recvStr(0);
            System.out.println(name + " received " + msg);
            if (msg == null || !msg.startsWith("OK world")) {
                return false;
            }

            return true;
        }
    }

    static class Dealer extends Thread
    {
        private final int            port;
        private final String         name;
        private final AtomicBoolean  result = new AtomicBoolean();
        private final CountDownLatch latch;

        public Dealer(String name, int port, CountDownLatch latch)
        {
            this.latch = latch;

            this.name = name;
            this.port = port;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assertThat(ctx, notNullValue());

            setName(name);
            System.out.println("Start " + name);

            Socket socket = ctx.socket(ZMQ.DEALER);
            boolean rc;
//            rc = socket.setImmediate(false);
//            assertThat(rc, is(true));
            rc = socket.setIdentity(name.getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));
            rc = socket.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            result.set(process(socket));
            latch.countDown();
            socket.close();
            ctx.close();
            System.out.println("Stop " + name);
        }

        private boolean process(Socket socket)
        {
            int count = 0;
            while (count < 2) {
                String msg = socket.recvStr(0);
                if (msg == null || !msg.startsWith("Client-")) {
                    System.out.println(name + " Wrong identity " + msg);
                    return false;
                }
                final String identity = msg;
                System.out.println(name + " received client identity " + identity);

                msg = socket.recvStr(0);
                if (msg == null || !msg.isEmpty()) {
                    System.out.println("Not bottom " + msg);
                    return false;
                }
                System.out.println(name + " received bottom " + msg);

                msg = socket.recvStr(0);
                if (msg == null) {
                    System.out.println(name + " Not data " + msg);
                    return false;
                }
                System.out.println(name + " received data " + msg);

                socket.send(identity, ZMQ.SNDMORE);
                socket.send((byte[]) null, ZMQ.SNDMORE);

                String response = "OK " + msg + " " + name;

                socket.send(response, 0);
                count++;
            }
            return true;
        }
    }

    static class Proxy extends Thread
    {
        private final int           frontendPort;
        private final int           backendPort;
        private final int           controlPort;
        private final AtomicBoolean result = new AtomicBoolean();

        Proxy(int frontendPort, int backendPort, int controlPort)
        {
            this.frontendPort = frontendPort;
            this.backendPort = backendPort;
            this.controlPort = controlPort;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assert (ctx != null);

            setName("Proxy");
            Socket frontend = ctx.socket(ZMQ.ROUTER);

            assertThat(frontend, notNullValue());
            frontend.bind("tcp://127.0.0.1:" + frontendPort);
//            frontend.setImmediate(false);

            Socket backend = ctx.socket(ZMQ.DEALER);
            assertThat(backend, notNullValue());
            backend.bind("tcp://127.0.0.1:" + backendPort);
//            backend.setImmediate(false);

            Socket control = ctx.socket(ZMQ.PAIR);
            assertThat(control, notNullValue());
            control.bind("tcp://127.0.0.1:" + controlPort);

            ZMQ.proxy(frontend, backend, null, control);

            frontend.close();
            backend.close();
            control.close();
            ctx.close();
            result.set(true);
        }

    }

    @Test
    public void testProxy() throws Exception
    {
        int frontendPort = Utils.findOpenPort();
        int backendPort = Utils.findOpenPort();
        int controlPort = Utils.findOpenPort();

        Proxy proxy = new Proxy(frontendPort, backendPort, controlPort);
        proxy.start();
        Thread.sleep(1000);

        CountDownLatch latch = new CountDownLatch(4);
        Dealer d1 = new Dealer("Dealer-A", backendPort, latch);
        Dealer d2 = new Dealer("Dealer-B", backendPort, latch);
        d1.start();
        d2.start();

        Client c1 = new Client("Client-X", frontendPort, latch);
        c1.start();

        Client c2 = new Client("Client-Y", frontendPort, latch);
        c2.start();

        try {
            latch.await(40, TimeUnit.SECONDS);
        }
        catch (Exception e) {
        }

        Context ctx = ZMQ.context(1);
        Socket control = ctx.socket(ZMQ.PAIR);
        control.connect("tcp://127.0.0.1:" + controlPort);
        control.send("TERMINATE");
        proxy.join();
        control.close();
        ctx.close();

        assertThat(c1.result.get(), is(true));
        assertThat(c2.result.get(), is(true));
        assertThat(d1.result.get(), is(true));
        assertThat(d2.result.get(), is(true));
        assertThat(proxy.result.get(), is(true));
    }
}
