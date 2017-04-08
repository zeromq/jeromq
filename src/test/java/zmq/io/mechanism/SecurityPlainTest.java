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

public class SecurityPlainTest
{
    private static class ZapHandler implements Runnable
    {
        private final SocketBase handler;

        public ZapHandler(SocketBase handler)
        {
            this.handler = handler;
        }

        @SuppressWarnings("unused")
        @Override
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
                Msg username = ZMQ.recv(handler, 0);
                Msg password = ZMQ.recv(handler, 0);

                assertThat(new String(version.data(), ZMQ.CHARSET), is("1.0"));
                assertThat(new String(mechanism.data(), ZMQ.CHARSET), is("PLAIN"));
                assertThat(new String(identity.data(), ZMQ.CHARSET), is("IDENT"));

                int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(3));
                ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(1));

                System.out.println("Sending ZAP PLAIN reply");
                if ("admin".equals(new String(username.data(), ZMQ.CHARSET))
                        && "password".equals(new String(password.data(), ZMQ.CHARSET))) {
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
                    ret = ZMQ.send(handler, "Invalid username or password", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(28));
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
    public void testPlainMechanismSecurity() throws IOException, InterruptedException
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

        //  Server socket will accept connections
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());

        ZMQ.setSocketOption(server, ZMQ.ZMQ_IDENTITY, "IDENT");
        ZMQ.setSocketOption(server, ZMQ.ZMQ_PLAIN_SERVER, true);
        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));

        String username = "admin";
        String password = "password";
        //  Check PLAIN security with correct username/password
        System.out.println("Test Correct PLAIN security");
        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_USERNAME, username);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_PASSWORD, password);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.bounce(server, client);
        ZMQ.close(client);

        //  Check PLAIN security with badly configured client (as_server)
        //  This will be caught by the plain_server class, not passed to ZAP
        System.out.println("Test badly configured PLAIN security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_SERVER, true);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check PLAIN security -- failed authentication
        System.out.println("Test wrong authentication PLAIN security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_USERNAME, "wronguser");
        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_PASSWORD, "wrongpass");

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        // Unauthenticated messages from a vanilla socket shouldn't be received
        System.out.println("Test unauthenticated from vanilla socket PLAIN security");

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
