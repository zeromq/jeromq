package zmq.io.mechanism;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import zmq.Ctx;
import zmq.Helper;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.TestUtils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class MechanismTester
{
    abstract static class TestContext
    {
        Ctx zctxt;
        SocketBase server;
        SocketBase client;
        SocketBase zapHandler;
        final String host = "tcp://127.0.0.1:*";
    }

    static <C extends TestContext> Boolean runTest(C testCtx, boolean withzap, Function<C, Boolean> tested,
            BiFunction<SocketBase, CompletableFuture<Boolean>, ZapHandler> zapProvider,
            Runnable configurator) throws InterruptedException
    {
        testCtx.zctxt = ZMQ.createContext();

        // Future to hold the result of Zap handler processing
        // true: zap allowed the connexion
        // false: zap refused the connexion
        // null: zap didn't do any processing
        CompletableFuture<Boolean> zapFuture = new CompletableFuture<>();
        Thread zapThread;

        if (withzap) {
            testCtx.zapHandler = ZMQ.socket(testCtx.zctxt, ZMQ.ZMQ_REP);
            ZapHandler handler = zapProvider.apply(testCtx.zapHandler, zapFuture);
            zapThread = handler.start();
            if (zapFuture.isDone()) {
                // zap future should not be already finished
                try {
                    assertThat(zapFuture.get().toString(), false);
                }
                catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        else {
            zapFuture.complete(null);
            zapThread = null;
            testCtx.zapHandler = null;
        }
        testCtx.server = ZMQ.socket(testCtx.zctxt, ZMQ.ZMQ_DEALER);
        assertThat(testCtx.server, notNullValue());

        testCtx.client = ZMQ.socket(testCtx.zctxt, ZMQ.ZMQ_DEALER);
        assertThat(testCtx.client, notNullValue());

        configurator.run();

        boolean isSuccess = tested.apply(testCtx);

        // Exchange messages only if both sockets are used, some tests uses a plain socket
        if (testCtx.server != null && testCtx.client != null) {
            if (isSuccess) {
                Helper.bounce(testCtx.server, testCtx.client);
            }
            else {
                Helper.expectBounceFail(testCtx.server, testCtx.client);
            }
        }

        Optional.ofNullable(testCtx.client).ifPresent(ZMQ::closeZeroLinger);
        Optional.ofNullable(testCtx.server).ifPresent(ZMQ::closeZeroLinger);

        //  Wait until ZAP handler terminates
        //Optional.ofNullable(testCtx.zapHandler).ifPresent(ZMQ::closeZeroLinger);
        Optional.ofNullable(zapThread).ifPresent(t -> {
            try {
                t.interrupt();
                t.join(5000);
            }
            catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });

        //  Shutdown
        ZMQ.term(testCtx.zctxt);

        try {
            return zapFuture.get(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T extends TestContext> boolean testRawSocket(T ctx)
    {
        try {
            boolean rc;

            int timeout = 250;
            ZMQ.setSocketOption(ctx.server, ZMQ.ZMQ_RCVTIMEO, timeout);

            rc = ZMQ.bind(ctx.server, ctx.host);
            assertThat(rc, is(true));
            int port = TestUtils.port((String) ZMQ.getSocketOptionExt(ctx.server, ZMQ.ZMQ_LAST_ENDPOINT));

            ZMQ.closeZeroLinger(ctx.client);
            ctx.client = null;

            try (Socket sock = new Socket("127.0.0.1", port)) {
                // send anonymous ZMTP/1.0 greeting
                OutputStream out = sock.getOutputStream();
                out.write(("1" + 0x00).getBytes(ZMQ.CHARSET));
                // send sneaky message that shouldn't be received
                out.write(
                        ("8" + 0x00 + "sneaky" + 0x00)
                                .getBytes(ZMQ.CHARSET));

                Msg msg = ZMQ.recv(ctx.server, 0);
                assertThat(msg, nullValue());

            }
            return false;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void checkOptions(Mechanisms mechanism, Consumer<Options> setOptions)
    {
        Options opt = new Options();
        setOptions.accept(opt);
        mechanism.check(opt);
    }

    private MechanismTester()
    {
        // static only class
    }
}
