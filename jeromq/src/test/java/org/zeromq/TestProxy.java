package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.util.ZData;

public class TestProxy
{
    static class Client implements Runnable
    {
        private final String        frontend;
        private final String        name;
        private final AtomicBoolean result = new AtomicBoolean();

        public Client(String name, String frontend)
        {
            this.name = name;
            this.frontend = frontend;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assertThat(ctx, notNullValue());

            Socket socket = ctx.socket(SocketType.REQ);
            boolean rc;
            rc = socket.setIdentity(id(name));
            assertThat(rc, is(true));

            System.out.println("Start " + name);
            Thread.currentThread().setName(name);

            rc = socket.connect(frontend);
            assertThat(rc, is(true));

            result.set(process(socket));
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
            return msg != null && msg.startsWith("OK world");
        }
    }

    static class Dealer implements Runnable
    {
        private final String        backend;
        private final String        name;
        private final AtomicBoolean result = new AtomicBoolean();

        public Dealer(String name, String backend)
        {
            this.name = name;
            this.backend = backend;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assertThat(ctx, notNullValue());

            Thread.currentThread().setName(name);
            System.out.println("Start " + name);

            Socket socket = ctx.socket(SocketType.DEALER);
            boolean rc;
            rc = socket.setIdentity(id(name));
            assertThat(rc, is(true));
            rc = socket.connect(backend);
            assertThat(rc, is(true));

            result.set(process(socket));
            socket.close();
            ctx.close();
            System.out.println("Stop " + name);
        }

        private boolean process(Socket socket)
        {
            int count = 0;
            while (count < 2) {
                byte[] msg = socket.recv(0);
                String msgAsString = new String(msg, ZMQ.CHARSET);
                if (msg == null || !msgAsString.startsWith("Client-")) {
                    System.out.println(name + " Wrong identity " + msgAsString);
                    return false;
                }
                final byte[] identity = msg;
                System.out.println(name + " received client identity " + ZData.strhex(identity));

                msg = socket.recv(0);
                msgAsString = new String(msg, ZMQ.CHARSET);
                if (msg == null || msg.length != 0) {
                    System.out.println("Not bottom " + Arrays.toString(msg));
                    return false;
                }
                System.out.println(name + " received bottom " + msgAsString);

                msg = socket.recv(0);
                if (msg == null) {
                    System.out.println(name + " Not data " + msg);
                    return false;
                }
                msgAsString = new String(msg, ZMQ.CHARSET);
                System.out.println(name + " received data " + msgAsString);

                socket.send(identity, ZMQ.SNDMORE);
                socket.send((byte[]) null, ZMQ.SNDMORE);

                String response = "OK " + msgAsString + " " + name;

                socket.send(response, 0);
                count++;
            }
            return true;
        }
    }

    static class Proxy extends Thread
    {
        private final String        frontend;
        private final String        backend;
        private final String        control;
        private final AtomicBoolean result = new AtomicBoolean();

        Proxy(String frontend, String backend, String control)
        {
            this.frontend = frontend;
            this.backend = backend;
            this.control = control;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            assert (ctx != null);

            setName("Proxy");
            Socket frontend = ctx.socket(SocketType.ROUTER);

            assertThat(frontend, notNullValue());
            frontend.bind(this.frontend);

            Socket backend = ctx.socket(SocketType.DEALER);
            assertThat(backend, notNullValue());
            backend.bind(this.backend);

            Socket control = ctx.socket(SocketType.PAIR);
            assertThat(control, notNullValue());
            control.bind(this.control);

            ZMQ.proxy(frontend, backend, null, control);

            frontend.close();
            backend.close();
            control.close();
            ctx.close();
            result.set(true);
        }
    }

    private static byte[] id(String name)
    {
        Random random = new Random();
        byte[] id = new byte[10 + random.nextInt(245)];
        random.nextBytes(id);
        System.arraycopy(name.getBytes(ZMQ.CHARSET), 0, id, 0, name.length());
        return id;
    }

    @Test
    public void testProxy() throws Exception
    {
        String frontend = "tcp://localhost:" + Utils.findOpenPort();
        String backend = "tcp://localhost:" + Utils.findOpenPort();
        String controlEndpoint = "tcp://localhost:" + Utils.findOpenPort();

        Proxy proxy = new Proxy(frontend, backend, controlEndpoint);
        proxy.start();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        Dealer d1 = new Dealer("Dealer-A", backend);
        Dealer d2 = new Dealer("Dealer-B", backend);
        executor.submit(d1);
        executor.submit(d2);

        Thread.sleep(1000);
        Client c1 = new Client("Client-X", frontend);
        Client c2 = new Client("Client-Y", frontend);
        executor.submit(c1);
        executor.submit(c2);

        executor.shutdown();
        executor.awaitTermination(40, TimeUnit.SECONDS);

        Context ctx = ZMQ.context(1);
        Socket control = ctx.socket(SocketType.PAIR);
        control.connect(controlEndpoint);
        control.send(ZMQ.PROXY_TERMINATE);
        proxy.join();
        control.close();
        ctx.close();

        assertThat(c1.result.get(), is(true));
        assertThat(c2.result.get(), is(true));
        assertThat(d1.result.get(), is(true));
        assertThat(d2.result.get(), is(true));
        assertThat(proxy.result.get(), is(true));
    }

    public void testRepeated() throws Exception
    {
        for (int idx = 0; idx < 470; ++idx) {
            System.out.println("---------- " + idx);
            testProxy();
            Thread.sleep(1000);
        }
    }
}
