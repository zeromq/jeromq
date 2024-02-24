package zmq.socket.pubsub;

import org.junit.Test;
import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class XpubXsubTest
{
    @Test(timeout = 5000)
    public void testXPubSub()
    {
        System.out.println("XPub - Sub");
        final Ctx ctx = zmq.ZMQ.createContext();
        assertThat(ctx, notNullValue());

        boolean rc;

        SocketBase sub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_SUB);
        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_SUBSCRIBE, "topic");
        assertThat(rc, is(true));
        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_SUBSCRIBE, "topix");
        assertThat(rc, is(true));

        SocketBase pub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XPUB);
        rc = zmq.ZMQ.bind(pub, "inproc://1");
        assertThat(rc, is(true));

        String endpoint = (String) ZMQ.getSocketOptionExt(pub, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        rc = zmq.ZMQ.connect(sub, endpoint);
        assertThat(rc, is(true));

        System.out.print("Send.");

        rc = pub.send(new Msg("topic".getBytes(ZMQ.CHARSET)), ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(true));

        rc = pub.send(new Msg("hop".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        System.out.print("Recv.");

        Msg msg = sub.recv(0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(5));

        msg = sub.recv(0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(3));

        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_UNSUBSCRIBE, "topix");
        assertThat(rc, is(true));

        rc = pub.send(new Msg("topix".getBytes(ZMQ.CHARSET)), ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(true));

        rc = pub.send(new Msg("hop".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_RCVTIMEO, 500);
        assertThat(rc, is(true));

        msg = sub.recv(0);
        assertThat(msg, nullValue());

        System.out.print("End.");

        zmq.ZMQ.close(sub);

        for (int idx = 0; idx < 2; ++idx) {
            rc = pub.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
            assertThat(rc, is(true));
            ZMQ.msleep(10);
        }
        zmq.ZMQ.close(pub);

        zmq.ZMQ.term(ctx);
        System.out.println("Done.");
    }

    @Test(timeout = 5000)
    public void testXPubXSub()
    {
        System.out.println("XPub - XSub");
        final Ctx ctx = zmq.ZMQ.createContext();
        assertThat(ctx, notNullValue());

        boolean rc;

        SocketBase pub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XPUB);
        rc = zmq.ZMQ.bind(pub, "inproc://1");
        assertThat(rc, is(true));

        String endpoint = (String) ZMQ.getSocketOptionExt(pub, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        SocketBase sub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XSUB);
        rc = zmq.ZMQ.connect(sub, endpoint);
        assertThat(rc, is(true));

        System.out.print("Send.");

        rc = sub.send(new Msg("\1topic".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        rc = pub.send(new Msg("topic".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        System.out.print("Recv.");

        rc = sub.send(new Msg("\0topic".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

//        rc = pub.send(new Msg("topix".getBytes(ZMQ.CHARSET)), ZMQ.ZMQ_SNDMORE);
//        assertThat(rc, is(true));
//
//        rc = pub.send(new Msg("hop".getBytes(ZMQ.CHARSET)), 0);
//        assertThat(rc, is(true));
//
//        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_RCVTIMEO, 500);
//        assertThat(rc, is(true));
//
//        msg = sub.recv(0);
//        assertThat(msg, nullValue());

        zmq.ZMQ.close(sub);
        zmq.ZMQ.close(pub);
        zmq.ZMQ.term(ctx);
        System.out.println("Done.");
    }

    @Test(timeout = 5000)
    public void testIssue476() throws InterruptedException, ExecutionException
    {
        System.out.println("Issue 476");

        final Ctx ctx = zmq.ZMQ.createContext();
        assertThat(ctx, notNullValue());

        boolean rc;
        final SocketBase proxyPub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XPUB);
        rc = proxyPub.bind("inproc://1");
        assertThat(rc, is(true));
        final SocketBase proxySub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XSUB);
        rc = proxySub.bind("inproc://2");
        assertThat(rc, is(true));

        final SocketBase ctrl = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_PAIR);
        rc = ctrl.bind("inproc://ctrl-proxy");
        assertThat(rc, is(true));

        ExecutorService service = Executors.newFixedThreadPool(1);

        Future<?> proxy = service.submit(() -> {
            ZMQ.proxy(proxySub, proxyPub, null, ctrl);
        });
        SocketBase sub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_SUB);
        rc = zmq.ZMQ.setSocketOption(sub, zmq.ZMQ.ZMQ_SUBSCRIBE, "topic");
        assertThat(rc, is(true));

        rc = zmq.ZMQ.connect(sub, "inproc://1");
        assertThat(rc, is(true));

        SocketBase pub = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_XPUB);

        rc = zmq.ZMQ.connect(pub, "inproc://2");
        assertThat(rc, is(true));

        rc = zmq.ZMQ.setSocketOption(sub, ZMQ.ZMQ_RCVTIMEO, 100);
        assertThat(rc, is(true));
        sub.recv(0);

        System.out.print("Send.");

        rc = pub.send(new Msg("topic".getBytes(ZMQ.CHARSET)), ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(true));

        rc = pub.send(new Msg("hop".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        System.out.print("Recv.");

        Msg msg = sub.recv(0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(5));

        msg = sub.recv(0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(3));

        System.out.print("End.");

        zmq.ZMQ.close(sub);

        for (int idx = 0; idx < 2; ++idx) {
            rc = pub.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
            assertThat(rc, is(true));
            ZMQ.msleep(10);
        }
        zmq.ZMQ.close(pub);

        final SocketBase command = zmq.ZMQ.socket(ctx, zmq.ZMQ.ZMQ_PAIR);
        rc = command.connect("inproc://ctrl-proxy");
        assertThat(rc, is(true));

        command.send(new Msg(ZMQ.PROXY_TERMINATE), 0);

        proxy.get();
        zmq.ZMQ.close(command);
        zmq.ZMQ.close(proxyPub);
        zmq.ZMQ.close(proxySub);
        zmq.ZMQ.close(ctrl);

        zmq.ZMQ.term(ctx);
        System.out.println("Done.");
    }
}
