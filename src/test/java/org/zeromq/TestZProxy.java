package org.zeromq;

import java.io.IOException;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZProxy
{
    private int frontPort;
    private int backPort;
    private int capturePort1;
    private int capturePort2;

    @Before
    public void setUp() throws IOException
    {
        frontPort    = Utils.findOpenPort();
        backPort     = Utils.findOpenPort();
        capturePort1 = Utils.findOpenPort();
        capturePort2 = Utils.findOpenPort();
    }

    @Test
    public void testAllOptionsAsync()
    {
        System.out.println("All options async");
        ZContext ctx = nullContext();
        testAllOptionsAsync(ctx, null);
        wait4NullContext(ctx);
    }

    @Test
    public void testAllOptionsAsyncNew()
    {
        System.out.println("All options async new");
        ZContext ctx = newContext();
        testAllOptionsAsync(ctx, null);
        wait4NewContext(ctx);
    }

    @Test
    public void testAllOptionsSync()
    {
        System.out.println("All options sync");
        ZContext ctx = nullContext();
        testAllOptionsSync(ctx, null);
        wait4NullContext(ctx);
    }

    @Test
    public void testAllOptionsSyncNew()
    {
        System.out.println("All options sync new");
        ZContext ctx = newContext();
        testAllOptionsSync(ctx, null);
        wait4NewContext(ctx);
    }

    @Test
    public void testAllOptionsSyncNewHot()
    {
        System.out.println("All options sync new hot");
        ZContext ctx = newContext();
        ZMsg hot = new ZMsg();
        hot.add("HOT");
        testAllOptionsSync(ctx, hot);
        wait4NewContext(ctx);
    }

    @Test
    public void testAllOptionsSyncNewCold()
    {
        System.out.println("All options sync new hot with restart");
        ZContext ctx = newContext();
        ZMsg hot = new ZMsg();
        hot.add("COLD");
        testAllOptionsSync(ctx, hot);
        wait4NewContext(ctx);
    }

    @Test
    public void testStateSync()
    {
        System.out.println("State sync");
        ZContext ctx = newContext();
        testStateSync(ctx);
        wait4NewContext(ctx);
    }

    @Test
    public void testStateSyncPause()
    {
        System.out.println("State sync pause");

        ZContext ctx = newContext();
        testStateSyncPause(ctx);
        wait4NewContext(ctx);
    }

    @Test
    public void testStateASync()
    {
        System.out.println("State async");

        ZContext ctx = newContext();
        testStateASync(ctx);
        wait4NewContext(ctx);
    }

    final ZProxy.Proxy provider = new  ZProxy.Proxy.SimpleProxy()
    {
        @Override
        public Socket create(ZContext ctx, ZProxy.Plug place, Object[] extraArgs)
        {
            Socket socket = null;
            if (place == ZProxy.Plug.FRONT) {
                socket = ctx.createSocket(ZMQ.ROUTER);
            }
            if (place == ZProxy.Plug.BACK) {
                socket = ctx.createSocket(ZMQ.DEALER);
            }
            return socket;
        }

        @Override
        public void configure(Socket socket, ZProxy.Plug place, Object[] extrArgs)
        {
            if (place == ZProxy.Plug.FRONT) {
                socket.bind("tcp://127.0.0.1:" + frontPort);
            }
            if (place == ZProxy.Plug.BACK) {
                socket.bind("tcp://127.0.0.1:" + backPort);
            }
            if (place == ZProxy.Plug.CAPTURE && socket != null) {
                socket.bind("tcp://127.0.0.1:" + capturePort1);
            }
        }

        @Override
        public boolean restart(ZMsg cfg, Socket socket, ZProxy.Plug place, Object[] extraArgs)
        {
//            System.out.println("HOT restart msg : " + cfg);
            if (place == ZProxy.Plug.FRONT) {
                socket.unbind("tcp://127.0.0.1:" + frontPort);
                waitSomeTime();
                socket.bind("tcp://127.0.0.1:" + frontPort);
            }
            if (place == ZProxy.Plug.BACK) {
                socket.unbind("tcp://127.0.0.1:" + backPort);
                waitSomeTime();
                socket.bind("tcp://127.0.0.1:" + backPort);
            }
            if (place == ZProxy.Plug.CAPTURE && socket != null) {
                socket.unbind("tcp://127.0.0.1:" + capturePort1);
                waitSomeTime();
                socket.bind("tcp://127.0.0.1:" + capturePort2);
            }
            String msg = cfg.popString();
            return "COLD".equals(msg);
        }

        @Override
        public boolean configure(Socket pipe, ZMsg cfg, Socket frontend, Socket backend, Socket capture, Object[] args)
        {
            assert (cfg.popString().equals("TEST-CONFIG"));
            ZMsg msg = new ZMsg();
            msg.add("TODO");
            msg.send(pipe);
            return true;
        }

        @Override
        public boolean custom(Socket pipe, String cmd, Socket frontend,
                Socket backend, Socket capture, Object[] args)
        {
            // TODO test custom commands
            return super.custom(pipe, cmd, frontend, backend, capture, args);
        }
    };

    @After
    public void testSignalsSelectors() throws Exception
    {
        wait4NullContext(null);
    }

    private ZContext nullContext()
    {
        return null;
    }

    private void wait4NullContext(ZContext ctx)
    {
        // it's necessary to wait a bit for unregistering selectors and signalers
        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS));
    }

    private void waitSomeTime()
    {
        // it's necessary to wait a bit for unregistering selectors and signalers
        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS));
    }

    private ZContext newContext()
    {
        return new ZContext();
    }

    private void wait4NewContext(ZContext ctx)
    {
        ctx.close();
    }

    private void testAllOptionsAsync(ZContext ctx, ZMsg hot)
    {
        ZProxy proxy = ZProxy.newProxy(ctx, "ProxyAsync" + (ctx == null ? "Null" : ""), provider, "ABRACADABRA", Arrays.asList("TEST"));

        final boolean async = false;
        String status = null;
        status = proxy.status(async);
        Assert.assertEquals("async status before any operation is not good!", ZProxy.ALIVE, status);

        status = proxy.start(async);
        Assert.assertEquals("Start async status is not good!", ZProxy.STOPPED, status);

        status = proxy.restart(hot);
        Assert.assertEquals("Restart async status is not good!", ZProxy.STARTED, status);

        status = proxy.pause(async);
        Assert.assertEquals("Pause async status is not good!", ZProxy.STARTED, status);

        status = proxy.restart(hot);
        Assert.assertEquals("Restart async status is not good!", ZProxy.PAUSED, status);

        status = proxy.stop(async);
        Assert.assertEquals("Stop async status is not good!", ZProxy.PAUSED, status);

        status = proxy.restart(hot);
        Assert.assertEquals("Restart async status is not good!", ZProxy.STOPPED, status);

        status = proxy.status(async);
        Assert.assertEquals("async status is not good!", ZProxy.STOPPED, status);

        ZMsg msg = new ZMsg();
        msg.add("TEST-CONFIG");
        ZMsg recvd = proxy.configure(msg);
        Assert.assertEquals("TODO", recvd.popString());
        status = proxy.exit();

        Assert.assertTrue("exit status is not good!", ZProxy.EXITED.equals(status));

        status = proxy.status();
        Assert.assertTrue("exit status is not good!", ZProxy.EXITED.equals(status));
    }

   private void testAllOptionsSync(ZContext ctx, ZMsg hot)
    {
        ZProxy proxy = ZProxy.newProxy(ctx, "ProxySync" + (ctx == null ? "Null" : ""), provider, "ABRACADABRA", Arrays.asList("TEST"));

        final boolean sync = true;
        String status = null;

        status = proxy.status(sync);
        Assert.assertEquals("sync status before any operation is not good!", ZProxy.STOPPED, status);
        status = proxy.start(sync);
        Assert.assertEquals("Start sync status is not good!", ZProxy.STARTED, status);

        status = proxy.restart(hot == null ? null : hot.duplicate());
        Assert.assertEquals("Restart sync status is not good!", ZProxy.STARTED, status);

        status = proxy.pause(sync);
        Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

        status = proxy.restart(hot == null ? null : hot.duplicate());
        Assert.assertEquals("Restart sync status is not good!", ZProxy.PAUSED, status);

        status = proxy.stop(sync);
        Assert.assertEquals("Stop sync status is not good!", ZProxy.STOPPED, status);

        status = proxy.restart(hot == null ? null : hot.duplicate());
        Assert.assertEquals("Restart sync status is not good!", ZProxy.STOPPED, status);

        status = proxy.status(sync);
        Assert.assertEquals("sync status is not good!", ZProxy.STOPPED, status);

        ZMsg msg = new ZMsg();
        msg.add("TEST-CONFIG");
        ZMsg recvd = proxy.configure(msg);
        Assert.assertEquals("TODO", recvd.popString());

        status = proxy.status(sync);
        Assert.assertEquals("sync status is not good!", ZProxy.STOPPED, status);

        status = proxy.pause(sync);
        Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

        status = proxy.exit();

        Assert.assertEquals("exit status is not good!", ZProxy.EXITED, status);
    }

   private void testStateSync(ZContext ctx)
   {
       final boolean sync = true;
       String status = null;
       ZProxy proxy = ZProxy.newProxy(ctx, "ProxyStateSync" + (ctx == null ? "Null" : ""), provider, "ABRACADABRA", Arrays.asList("TEST"));

       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.STARTED, status);

       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.STOPPED, status);

       waitSomeTime();

       // start the proxy after stopping it
       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.STARTED, status);

       waitSomeTime();

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.STOPPED, status);

       waitSomeTime();

       // pause the proxy after stopping it
       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

       status = proxy.exit();

       Assert.assertEquals("exit status is not good!", ZProxy.EXITED, status);
   }

   private void testStateSyncPause(ZContext ctx)
   {
       final boolean sync = true;
       String status = null;
       ZProxy proxy = ZProxy.newProxy(ctx, "ProxyStatePauseSync" + (ctx == null ? "Null" : ""), provider, "ABRACADABRA", Arrays.asList("TEST"));

       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.STARTED, status);

       waitSomeTime();

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.STOPPED, status);

       waitSomeTime();

       // start the proxy after stopping it
       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.STARTED, status);

       waitSomeTime();

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.STOPPED, status);

       waitSomeTime();

       // pause the proxy after stopping it
       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.PAUSED, status);

       status = proxy.exit();

       Assert.assertEquals("exit status is not good!", ZProxy.EXITED, status);
   }

   private void testStateASync(ZContext ctx)
   {
       final boolean sync = false;
       String status = null;
       ZProxy proxy = ZProxy.newProxy(ctx, "ProxyStateASync" + (ctx == null ? "Null" : ""), provider, "ABRACADABRA", Arrays.asList("TEST"));

       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.ALIVE, status);

       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.STARTED, status);

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.PAUSED, status);

       waitSomeTime();

       // start the proxy after stopping it
       status = proxy.start(sync);
       Assert.assertEquals("Start sync status is not good!", ZProxy.STOPPED, status);

       waitSomeTime();

       status = proxy.stop(sync);
       Assert.assertEquals("Stop sync status is not good!", ZProxy.STARTED, status);

       waitSomeTime();

       // pause the proxy after stopping it
       status = proxy.pause(sync);
       Assert.assertEquals("Pause sync status is not good!", ZProxy.STOPPED, status);

       status = proxy.exit();

       Assert.assertEquals("exit status is not good!", ZProxy.EXITED, status);
   }
}
