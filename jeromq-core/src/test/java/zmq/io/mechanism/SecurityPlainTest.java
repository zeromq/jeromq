package zmq.io.mechanism;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.TestUtils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecurityPlainTest
{
    private static class PlainTestContext extends MechanismTester.TestContext
    {
        String user;
        String password;
    }

    private Boolean runTest(boolean withzap, Function<PlainTestContext, Boolean> tested) throws InterruptedException
    {
        PlainTestContext testCtx = new PlainTestContext();
        testCtx.user = "admin";
        testCtx.password = "password";

        BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider = (s, f) -> new ZapHandler(s, f, "admin", "password");
        Runnable configurator = () -> {
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_IDENTITY, "IDENT");
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_PLAIN_SERVER, true);
        };

        return MechanismTester.runTest(testCtx, withzap, tested, zapProvider, configurator);
    }

    public boolean runValid(PlainTestContext tctxt)
    {
        boolean rc;

        rc = ZMQ.bind(tctxt.server, tctxt.host);
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
        ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_USERNAME, tctxt.user);
        ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_PASSWORD, tctxt.password);

        rc = ZMQ.connect(tctxt.client, host);
        assertThat(rc, is(true));
        return true;
    }

    @Test(timeout = 5000)
    public void testNoZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(false, this::runValid);
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, this::runValid);
        assertThat(status, is(true));
    }

    @Test(timeout = 5000)
    public void testZapInverted() throws InterruptedException
    {
        //  When ZAP is not used, always accept
        Boolean status = runTest(false, tctxt -> {
            boolean rc;

            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_USERNAME, tctxt.user);
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_PASSWORD, tctxt.password);

            rc = ZMQ.bind(tctxt.client, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.client, ZMQ.ZMQ_LAST_ENDPOINT);

            rc = ZMQ.connect(tctxt.server, host);
            assertThat(rc, is(true));

            return true;
        });
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testBothServer() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_SERVER, true);

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testFailedLoginZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_USERNAME, "wronguser");
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_PASSWORD, "wrongpass");

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(status, is(false));
    }

    @Test(timeout = 5000)
    public void testSuccessBadPasswordNoZap() throws InterruptedException
    {
        //  When ZAP is not used, always accept
        Boolean status = runTest(false, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_USERNAME, "wronguser");
            ZMQ.setSocketOption(tctxt.client, ZMQ.ZMQ_PLAIN_PASSWORD, "wrongpass");

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return true;
        });
        assertThat(status, nullValue());
    }

    @Test
    public void testRawSocket() throws InterruptedException
    {
        // Unauthenticated messages from a vanilla socket shouldn't be received
        Boolean zapCheck = runTest(false, MechanismTester::testRawSocket);
        assertThat(zapCheck, nullValue());
    }

    @Test(timeout = 5000)
    public void testLongCredentials() throws InterruptedException
    {
        //  Username and password lengths are unsigned octets
        Boolean status = runTest(false, tctxt -> {
            char[] credential = new char[200];
            Arrays.fill(credential, 'a');
            tctxt.user = new String(credential);
            tctxt.password = new String(credential);
            return runValid(tctxt);
        });
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testHelloWithoutPassword() throws InterruptedException
    {
        //  Empty username, and nothing after it
        byte[] hello = {0x04, 0x07, 0x05, 'H', 'E', 'L', 'L', 'O', 0x00};
        Boolean status = runTest(false, tctxt -> rejectHello(tctxt, hello));
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testHelloWithTruncatedUsername() throws InterruptedException
    {
        //  Username announced with 128 bytes, and nothing after it
        byte[] hello = {0x04, 0x07, 0x05, 'H', 'E', 'L', 'L', 'O', (byte) 0x80};
        Boolean status = runTest(false, tctxt -> rejectHello(tctxt, hello));
        assertThat(status, nullValue());
    }

    private boolean rejectHello(PlainTestContext tctxt, byte[] hello)
    {
        boolean rc = ZMQ.bind(tctxt.server, tctxt.host);
        assertThat(rc, is(true));
        int port = TestUtils.port((String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT));

        ZMQ.closeZeroLinger(tctxt.client);
        tctxt.client = null;

        //  ZMTP/3.0 greeting announcing the PLAIN mechanism
        byte[] greeting = new byte[64];
        greeting[0] = (byte) 0xff;
        greeting[9] = 0x7f;
        greeting[10] = 3;
        System.arraycopy("PLAIN".getBytes(ZMQ.CHARSET), 0, greeting, 12, 5);

        try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.setSoTimeout(2000);
            OutputStream out = sock.getOutputStream();
            out.write(greeting);
            out.write(hello);
            out.flush();

            //  The server closes the connection
            InputStream in = sock.getInputStream();
            while (in.read() != -1) {
                //  Skip whatever the server sent
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent1()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, null);
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, "plainPassword");
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent2()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, "plainUsername");
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, null);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent3()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, String.format("%256d", 1));
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, "plainPassword");
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent4()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, "plainUsername");
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, String.format("%256d", 1));
        });
    }

    @Test
    public void consistent()
    {
        MechanismTester.checkOptions(Mechanisms.PLAIN, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, "plainUsername");
            opt.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, "plainPassword");
        });
    }
}
