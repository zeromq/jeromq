package org.zeromq;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZProxy
{
    @Test
    public void testAllOptions()
    {
        final ZProxy.Proxy provider = new ZProxy.Proxy.SimpleProxy()
        {
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

            public void configure(Socket socket, ZProxy.Plug place, Object[] extrArgs)
            {
                if (place == ZProxy.Plug.FRONT) {
                    socket.bind("tcp://127.0.0.1:6660");
                }
                if (place == ZProxy.Plug.BACK) {
                    socket.bind("tcp://127.0.0.1:6661");
                }
                if (place == ZProxy.Plug.CAPTURE && socket != null) {
                    socket.bind("tcp://127.0.0.1:4263");
                }
            }

            @Override
            public boolean restart(ZMsg cfg, Socket socket, ZProxy.Plug place, Object[] extraArgs)
            {
                if (place == ZProxy.Plug.FRONT) {
                    socket.unbind("tcp://127.0.0.1:6660");
                    socket.bind("tcp://127.0.0.1:6660");
                }
                if (place == ZProxy.Plug.BACK) {
                    socket.unbind("tcp://127.0.0.1:6661");
                    socket.bind("tcp://127.0.0.1:6661");
                }
                if (place == ZProxy.Plug.CAPTURE && socket != null) {
                    socket.unbind("tcp://127.0.0.1:4263");
                    socket.bind("tcp://127.0.0.1:5347");
                }
                return false;
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

        ZProxy proxy = ZProxy.newProxy(null, "ProxyOne", provider, "ABRACADABRA", Arrays.asList("TEST"));

        final boolean async = false;
        final boolean sync = true;
        String status = null;
        status = proxy.status(async);
        Assert.assertEquals("async status before any operation is not good!", ZProxy.ALIVE, status);

        status = proxy.start(async);
        Assert.assertEquals("Start async status is not good!", ZProxy.STOPPED, status);

        status = proxy.pause(async);
        Assert.assertEquals("Pause async status is not good!", ZProxy.STARTED, status);

        status = proxy.stop(async);
        Assert.assertEquals("Stop async status is not good!", ZProxy.PAUSED, status);

        status = proxy.status(async);
        Assert.assertEquals("async status is not good!", ZProxy.STOPPED, status);

        ZMsg msg = new ZMsg();
        msg.add("TEST-CONFIG");
        ZMsg recvd = proxy.configure(msg);
        Assert.assertEquals("TODO", recvd.popString());
        System.out.println("Received config");
        proxy.exit(async);

//        rc = pipe.send("boom ?!");
//        assert (!rc);
        System.out.println();
    }
}
