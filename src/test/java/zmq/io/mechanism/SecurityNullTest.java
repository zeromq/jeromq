package zmq.io.mechanism;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class SecurityNullTest
{
    private static class ZapHandler implements Runnable
    {
        private final SocketBase handler;

        public ZapHandler(SocketBase handler)
        {
            this.handler = handler;
        }

        @Override
        @SuppressWarnings("unused")
        public void run()
        {
            //  Process ZAP requests forever
            while (true) {
                Msg version = ZMQ.recv(handler, 0);
                if (version == null) {
                    break; //  Terminating
                }
                Msg sequence = ZMQ.recv(handler, 0);
                Msg domain = ZMQ.recv(handler, 0);
                Msg address = ZMQ.recv(handler, 0);
                Msg identity = ZMQ.recv(handler, 0);
                Msg mechanism = ZMQ.recv(handler, 0);

                assertThat(new String(version.data(), ZMQ.CHARSET), is("1.0"));
                assertThat(new String(mechanism.data(), ZMQ.CHARSET), is("NULL"));

                int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(3));
                ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(1));

                System.out.println("Sending ZAP NULL reply");
                if ("TEST".equals(new String(domain.data(), ZMQ.CHARSET))) {
                    ret = ZMQ.send(handler, "200", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    ret = ZMQ.send(handler, "OK", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(2));
                    ret = ZMQ.send(handler, "anonymous", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(9));
                    ret = ZMQ.send(handler, "", 0);
                    assertThat(ret, is(0));
                }
                else {
                    ret = ZMQ.send(handler, "400", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    ret = ZMQ.send(handler, "BAD DOMAIN", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(10));
                    ret = ZMQ.send(handler, "", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(0));
                    ret = ZMQ.send(handler, "", 0);
                    assertThat(ret, is(0));
                }
            }

            ZMQ.closeZeroLinger(handler);
        }
    }

    @Test
    public void testNullMechanismSecurity() throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        Ctx ctx = ZMQ.createContext();

        //  Spawn ZAP handler
        //  We create and bind ZAP socket in main thread to avoid case
        //  where child thread does not start up fast enough.
        SocketBase handler = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(handler, notNullValue());
        boolean rc = ZMQ.bind(handler, "inproc://zeromq.zap.01");
        assertThat(rc, is(true));

        Thread thread = new Thread(new ZapHandler(handler));
        thread.start();

        //  We bounce between a binding server and a connecting client

        //  We first test client/server with no ZAP domain
        //  Libzmq does not call our ZAP handler, the connect must succeed
        System.out.println("Test NO ZAP domain");
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());
        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.bounce(server, client);
        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        //  Now define a ZAP domain for the server; this enables
        //  authentication. We're using the wrong domain so this test
        //  must fail.
        System.out.println("Test WRONG ZAP domain");
        server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(server, ZMQ.ZMQ_ZAP_DOMAIN, "WRONG");
        port = Utils.findOpenPort();
        host = "tcp://127.0.0.1:" + port;
        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        //  Now use the right domain, the test must pass
        System.out.println("Test RIGHT ZAP domain");
        server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(server, ZMQ.ZMQ_ZAP_DOMAIN, "TEST");
        port = Utils.findOpenPort();
        host = "tcp://127.0.0.1:" + port;
        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.bounce(server, client);
        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        // Unauthenticated messages from a vanilla socket shouldn't be received
        server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());
        ZMQ.setSocketOption(server, ZMQ.ZMQ_ZAP_DOMAIN, "WRONG");
        port = Utils.findOpenPort();
        host = "tcp://127.0.0.1:" + port;
        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));

        Socket sock = new Socket("127.0.0.1", port);
        // send anonymous ZMTP/1.0 greeting
        OutputStream out = sock.getOutputStream();
        out.write(new StringBuilder().append(0x01).append(0x00).toString().getBytes(ZMQ.CHARSET));
        // send sneaky message that shouldn't be received
        out.write(
                  new StringBuilder().append(0x08).append(0x00).append("sneaky").append(0x00).toString()
                          .getBytes(ZMQ.CHARSET));
        int timeout = 250;
        ZMQ.setSocketOption(server, ZMQ.ZMQ_RCVTIMEO, timeout);

        Msg msg = ZMQ.recv(server, 0);
        assertThat(msg, nullValue());

        sock.close();
        ZMQ.closeZeroLinger(server);

        //  Shutdown
        ZMQ.term(ctx);

        //  Wait until ZAP handler terminates
        thread.join();
    }
}
