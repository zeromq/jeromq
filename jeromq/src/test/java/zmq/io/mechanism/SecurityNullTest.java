package zmq.io.mechanism;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import zmq.SocketBase;
import zmq.ZMQ;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecurityNullTest
{
    private static class NullTestContext extends MechanismTester.TestContext
    {
        // Nothing to add
    }

    private Boolean runTest(boolean withzap, Function<NullTestContext, Boolean> tested) throws InterruptedException
    {
        NullTestContext testCtx = new NullTestContext();

        BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider = ZapHandler::new;
        Runnable configurator = () -> ZMQ.setSocketOption(testCtx.server, ZMQ.ZMQ_IDENTITY, "IDENT");

        return MechanismTester.runTest(testCtx, withzap, tested, zapProvider, configurator);
    }

    @Test(timeout = 5000)
    public void testNoZap() throws InterruptedException
    {
        //  We first test client/server with no ZAP domain
        Boolean status = runTest(false, tctxt -> {
            boolean rc;

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return true;
        });
        assertThat(status, nullValue());
    }

    @Test(timeout = 5000)
    public void testZap() throws InterruptedException
    {
        //  Now use the right domain, the test must pass

        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            ZMQ.setSocketOption(tctxt.server, ZMQ.ZMQ_ZAP_DOMAIN, "TEST");

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return true;
        });
        assertThat(status, is(true));
    }

    @Test(timeout = 5000)
    public void testWrongDomain() throws InterruptedException
    {
        //  Define a ZAP domain for the server; this enables
        //  authentication. We're using the wrong domain so this test
        //  must fail.

        Boolean status = runTest(true, tctxt -> {
            boolean rc;

            ZMQ.setSocketOption(tctxt.server, ZMQ.ZMQ_ZAP_DOMAIN, "WRONG");

            rc = ZMQ.bind(tctxt.server, tctxt.host);
            assertThat(rc, is(true));

            String host = (String) ZMQ.getSocketOptionExt(tctxt.server, ZMQ.ZMQ_LAST_ENDPOINT);

            rc = ZMQ.connect(tctxt.client, host);
            assertThat(rc, is(true));
            return false;
        });
        assertThat(status, is(false));
    }

    @Test(timeout = 5000)
    public void testRawSocket() throws InterruptedException
    {
        // Unauthenticated messages from a vanilla socket shouldn't be received
        Boolean zapCheck = runTest(false, MechanismTester::testRawSocket);
        assertThat(zapCheck, nullValue());
    }
}
