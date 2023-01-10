package zmq.io.mechanism;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import zmq.SocketBase;
import zmq.ZMQ;
import zmq.io.mechanism.curve.Curve;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecurityCurveTest
{
    private static class CurveTestContext extends MechanismTester.TestContext
    {
        String serverPublic;
        String serverSecret;
        String clientPublic;
        String clientSecret;
    }

    private Boolean runTest(boolean withzap, Function<CurveTestContext, Boolean> tested) throws InterruptedException
    {
        CurveTestContext testCtx = new CurveTestContext();
        //  Generate new keypairs for this test
        Curve cryptoBox = new Curve();
        String[] clientKeys = cryptoBox.keypairZ85();
        testCtx.clientPublic = clientKeys[0];
        testCtx.clientSecret = clientKeys[1];

        String[] serverKeys = cryptoBox.keypairZ85();
        testCtx.serverPublic = serverKeys[0];
        testCtx.serverSecret = serverKeys[1];

        BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider = (s, f) -> new ZapHandler(s, f, testCtx.clientPublic);
        Runnable configurator = () -> {
            // Preconfigure server with valid identity, might be changed for individual tests
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_CURVE_SERVER, true);
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_CURVE_SECRETKEY, testCtx.serverSecret);
            ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_IDENTITY, "IDENT");

            // Preconfigure client with valid identity, might be changed for individual tests
            ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_CURVE_SERVERKEY, testCtx.serverPublic);
            ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_CURVE_PUBLICKEY, testCtx.clientPublic);
            ZMQ.setSocketOption(testCtx.client, ZMQ.ZMQ_CURVE_SECRETKEY, testCtx.clientSecret);
        };

        return MechanismTester.runTest(testCtx, withzap, tested, zapProvider, configurator);
    }

    private boolean doSuccess(CurveTestContext ctx)
    {
        boolean rc;

        rc = ZMQ.bind(ctx.server, ctx.host);
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
        rc = ZMQ.connect(ctx.client, host);
        assertThat(rc, is(true));

        return true;
    }

    @Test
    public void testSuccessWithZap() throws InterruptedException
    {
        assertThat(runTest(true, this::doSuccess), is(true));
    }

    @Test
    public void testSuccessNoZap() throws InterruptedException
    {
        assertThat(runTest(false, this::doSuccessInverted), nullValue());
    }

    private boolean doSuccessInverted(CurveTestContext ctx)
    {
        boolean rc;

        rc = ZMQ.bind(ctx.client, ctx.host);
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(ctx.client, ZMQ.ZMQ_LAST_ENDPOINT);

        rc = ZMQ.connect(ctx.server, host);
        assertThat(rc, is(true));

        return true;
    }

    @Test
    public void testSuccessInvertedWithZap() throws InterruptedException
    {
        assertThat(runTest(true, this::doSuccessInverted), is(true));
    }

    @Test
    public void testSuccessInvertedNoZap() throws InterruptedException
    {
        assertThat(runTest(false, this::doSuccessInverted), nullValue());
    }

    @Test
    public void testGarbageClientSecretKeyZap() throws InterruptedException
    {
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;
            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_CURVE_SECRETKEY, "0000000000000000000000000000000000000000");
            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);

            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testGarbageServerSecretKeyZap() throws InterruptedException
    {
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            ZMQ.setSocketOption(ctx.server, ZMQ.ZMQ_CURVE_SECRETKEY, "0000000000000000000000000000000000000000");
            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testBogusClientKey() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            Curve cryptoBox = new Curve();
            String[] bogus = cryptoBox.keypairZ85();
            String bogusPublic = bogus[0];
            String bogusSecret = bogus[1];

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_CURVE_PUBLICKEY, bogusPublic);
            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_CURVE_SECRETKEY, bogusSecret);
            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, is(false));
    }

    @Test(timeout = 5000)
    public void testBogusClientInvertedKey() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(true, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.client, ctx.host);
            assertThat(rc, is(true));

            Curve cryptoBox = new Curve();
            String[] bogus = cryptoBox.keypairZ85();
            String bogusPublic = bogus[0];
            String bogusSecret = bogus[1];

            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_CURVE_PUBLICKEY, bogusPublic);
            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_CURVE_SECRETKEY, bogusSecret);
            String host = (String) ZMQ.getSocketOptionExt(ctx.client, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.server, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, is(false));
    }

    @Test
    public void testNullClient() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(false, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.closeZeroLinger(ctx.client);
            ctx.client = ZMQ.socket(ctx.zctxt, ZMQ.ZMQ_DEALER);

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testPlainClient() throws InterruptedException
    {
        //  Check CURVE security with bogus client credentials
        //  This must be caught by the ZAP handler
        Boolean zapCheck = runTest(false, ctx -> {
            boolean rc;

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));

            ZMQ.closeZeroLinger(ctx.client);
            ctx.client = ZMQ.socket(ctx.zctxt, ZMQ.ZMQ_DEALER);
            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_PLAIN_USERNAME, "user");
            ZMQ.setSocketOption(ctx.client, ZMQ.ZMQ_PLAIN_PASSWORD, "pass");

            String host = (String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT);
            rc = ZMQ.connect(ctx.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(zapCheck, nullValue());
    }

    @Test
    public void testRawSocket() throws InterruptedException
    {
        // Unauthenticated messages from a vanilla socket shouldn't be received
        Boolean zapCheck = runTest(false, MechanismTester::testRawSocket);
        assertThat(zapCheck, nullValue());
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent1()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, null);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent2()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, null);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[32]);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent3()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[31]);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent4()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[31]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[32]);
        });
    }

    @Test(expected = IllegalStateException.class)
    public void inconsistent5()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY, new byte[31]);
        });
    }

    @Test
    public void consistent1()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[32]);
        });
    }

    @Test
    public void consistent2()
    {
        MechanismTester.checkOptions(Mechanisms.CURVE, opt -> {
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, new byte[32]);
            opt.setSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY, new byte[32]);
        });
    }
}
