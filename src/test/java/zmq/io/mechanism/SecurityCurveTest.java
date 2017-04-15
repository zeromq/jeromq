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
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.mechanism.curve.Curve;
import zmq.util.Utils;
import zmq.util.Z85;

public class SecurityCurveTest
{
    private static class ZapHandler implements Runnable
    {
        private final SocketBase handler;
        private final String     clientPublic;

        public ZapHandler(SocketBase handler, String clientPublic)
        {
            this.handler = handler;
            this.clientPublic = clientPublic;
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
                Msg clientKey = ZMQ.recv(handler, 0);

                final String clientKeyText = Z85.encode(clientKey.data(), clientKey.size());

                assertThat(new String(version.data(), ZMQ.CHARSET), is("1.0"));
                assertThat(new String(mechanism.data(), ZMQ.CHARSET), is("CURVE"));
                assertThat(new String(identity.data(), ZMQ.CHARSET), is("IDENT"));

                int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(3));
                ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(1));

                System.out.println("Sending ZAP CURVE reply");
                if (clientKeyText.equals(clientPublic)) {
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
                    ret = ZMQ.send(handler, "Invalid client public key", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(25));
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
    public void testCurveMechanismSecurity() throws IOException, InterruptedException
    {
        boolean installed = Curve.installed();
        if (!installed) {
            System.out.println("CURVE encryption not installed, skipping test");
            return;
        }

        Curve cryptoBox = new Curve();
        //  Generate new keypairs for this test
        //  We'll generate random test keys at startup
        String[] clientKeys = cryptoBox.keypairZ85();
        String clientPublic = clientKeys[0];
        String clientSecret = clientKeys[1];

        String[] serverKeys = cryptoBox.keypairZ85();
        String serverPublic = serverKeys[0];
        String serverSecret = serverKeys[1];

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

        Thread thread = new Thread(new ZapHandler(handler, clientPublic));
        thread.start();

        //  Server socket will accept connections
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());

        ZMQ.setSocketOption(server, ZMQ.ZMQ_CURVE_SERVER, true);
        ZMQ.setSocketOption(server, ZMQ.ZMQ_CURVE_SECRETKEY, serverSecret);
        ZMQ.setSocketOption(server, ZMQ.ZMQ_IDENTITY, "IDENT");
        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));

        //  Check CURVE security with valid credentials
        System.out.println("Test Correct CURVE security");
        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, serverPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, clientPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, clientSecret);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.bounce(server, client);
        ZMQ.close(client);

        //  Check CURVE security with a garbage server key
        //  This will be caught by the curve_server class, not passed to ZAP
        System.out.println("Test bad server key CURVE security");
        String garbageKey = "0000000000000000000000000000000000000000";
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, garbageKey);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, clientPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, clientSecret);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check CURVE security with a garbage client public key
        //  This will be caught by the curve_server class, not passed to ZAP
        System.out.println("Test bad client public key CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, serverPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, garbageKey);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, clientSecret);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check CURVE security with a garbage client secret key
        //  This will be caught by the curve_server class, not passed to ZAP
        System.out.println("Test bad client public key CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, serverPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, clientPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, garbageKey);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        String[] bogus = cryptoBox.keypairZ85();
        String bogusPublic = bogus[0];
        String bogusSecret = bogus[1];

        System.out.println("Test bad client credentials CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, serverPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, bogusPublic);
        ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, bogusSecret);

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check CURVE security with NULL client credentials
        //  This must be caught by the curve_server class, not passed to ZAP
        System.out.println("Test NULL client with CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        //  Check CURVE security with PLAIN client credentials
        //  This must be caught by the curve_server class, not passed to ZAP
        System.out.println("Test PLAIN client with CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_USERNAME, "user");
        ZMQ.setSocketOption(client, ZMQ.ZMQ_PLAIN_PASSWORD, "pass");

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        Helper.expectBounceFail(server, client);
        ZMQ.closeZeroLinger(client);

        // Unauthenticated messages from a vanilla socket shouldn't be received
        System.out.println("Test unauthenticated from vanilla socket CURVE security");

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

        //  Check return codes for invalid buffer sizes
        // TODO
        System.out.println("Test return codes for invalid buffer sizes with CURVE security");
        client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SERVERKEY, new byte[123]);
        assertThat(rc, is(false));
        assertThat(client.errno.get(), is(ZError.EINVAL));

        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[123]);
        assertThat(rc, is(false));
        assertThat(client.errno.get(), is(ZError.EINVAL));

        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_CURVE_SECRETKEY, new byte[123]);
        assertThat(rc, is(false));
        assertThat(client.errno.get(), is(ZError.EINVAL));

        ZMQ.closeZeroLinger(client);
        ZMQ.closeZeroLinger(server);

        //  Shutdown
        ZMQ.term(ctx);
        //  Wait until ZAP handler terminates
        thread.join();
    }
}
