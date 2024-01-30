package zmq.io.mechanism;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Z85;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class ZapHandler implements Runnable
{
    private final SocketBase handler;
    private final String     mechanism;
    private final String     clientPublic;
    private final String     username;
    private final String     password;
    private final CompletableFuture<Boolean> zapFuture;

    public ZapHandler(SocketBase handler, CompletableFuture<Boolean> zapFuture, String clientPublic)
    {
        this.handler = handler;
        this.clientPublic = clientPublic;
        this.zapFuture = zapFuture;
        this.username = null;
        this.password = null;
        this.mechanism = "CURVE";
    }

    public ZapHandler(SocketBase handler, CompletableFuture<Boolean> zapFuture, String username, String password)
    {
        this.handler = handler;
        this.clientPublic = null;
        this.zapFuture = zapFuture;
        this.username = username;
        this.password = password;
        this.mechanism = "PLAIN";
    }

    public ZapHandler(SocketBase handler, CompletableFuture<Boolean> zapFuture)
    {
        this.handler = handler;
        this.clientPublic = null;
        this.zapFuture = zapFuture;
        this.username = null;
        this.password = null;
        this.mechanism = "NULL";
    }

    public Thread start() throws InterruptedException
    {
        CountDownLatch zapStarted = new CountDownLatch(1);
        //  Spawn ZAP handler
        Thread zapThread = new Thread(() -> {
            try {
                assertThat(handler, notNullValue());
                boolean rc = ZMQ.bind(handler, "inproc://zeromq.zap.01");
                assertThat(rc, is(true));
            }
            catch (Exception | AssertionError ex) {
                ex.printStackTrace();
                zapFuture.completeExceptionally(ex);
            }
            finally {
                zapStarted.countDown();
            }
            run();
        });
        zapThread.setUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            zapFuture.completeExceptionally(e);
        });
        zapThread.setName("ZAPHANDLER");
        zapThread.start();
        assertThat(zapStarted.await(5, TimeUnit.SECONDS), is(true));
        return zapThread;
    }

    @Override
    public void run()
    {
        //  Process ZAP requests forever
        try {
            while (true) {
                Msg version = ZMQ.recv(handler, 0);
                if (version != null) {
                    assertThat("No more message", version.hasMore());
                    assertThat(new String(version.data(), ZMQ.CHARSET), is("1.0"));
                    Msg sequence = ZMQ.recv(handler, 0);
                    assertThat("No more message", sequence.hasMore());
                    Msg domain = ZMQ.recv(handler, 0);
                    assertThat("No more message", domain.hasMore());
                    String clientDomain = new String(domain.data(), ZMQ.CHARSET);
                    Msg address = ZMQ.recv(handler, 0);
                    assertThat("No more message", address.hasMore());
                    Msg identity = ZMQ.recv(handler, 0);
                    assertThat("No more message", identity.hasMore());
                    assertThat(new String(identity.data(), ZMQ.CHARSET), is("IDENT"));
                    Msg mechanismMsg = ZMQ.recv(handler, 0);
                    assertThat("No more message",  "NULL".equals(mechanism) || mechanismMsg.hasMore());
                    String clientMechanism = new String(mechanismMsg.data(), ZMQ.CHARSET);
                    assertThat(clientMechanism, is(mechanism));

                    String clientKeyText = null;
                    String clientUsername = null;
                    String clientPassword = null;
                    if ("CURVE".equals(mechanism)) {
                        Msg clientKey = ZMQ.recv(handler, 0);
                        assertThat("More message", ! clientKey.hasMore());
                        clientKeyText = Z85.encode(clientKey.data(), clientKey.size());
                    }
                    else if ("PLAIN".equals(mechanism)) {
                        Msg username = ZMQ.recv(handler, 0);
                        assertThat("No more message", username.hasMore());
                        Msg password = ZMQ.recv(handler, 0);
                        assertThat("No more message", ! password.hasMore());
                        clientUsername = new String(username.data(), ZMQ.CHARSET);
                        clientPassword = new String(password.data(), ZMQ.CHARSET);
                    }

                    int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(1));

                    boolean authentified;
                    String errorMessage;
                    String userId;
                    if ("CURVE".equals(mechanism)) {
                        authentified = clientKeyText.equals(clientPublic);
                        errorMessage = "Invalid client public key";
                        userId = clientPublic;
                    }
                    else if ("PLAIN".equals(mechanism)) {
                        authentified = username.equals(clientUsername) && password.equals(clientPassword);
                        errorMessage = "Invalid username or password";
                        userId = clientUsername;
                    }
                    else if ("NULL".equals(mechanism)) {
                        authentified = "TEST".equals(clientDomain);
                        errorMessage = "BAD DOMAIN";
                        userId = "";
                    }
                    else {
                        authentified = false;
                        errorMessage = "Unknown mechanism";
                        userId = "";
                    }
                    ret = ZMQ.send(handler, authentified ? "200" : "400", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    if (authentified) {
                        ret = ZMQ.send(handler, "OK", ZMQ.ZMQ_SNDMORE);
                        assertThat(ret, is(2));
                        ret = ZMQ.send(handler, userId, ZMQ.ZMQ_SNDMORE);
                        assertThat(ret, is(userId.length()));
                    }
                     else {
                        ret = ZMQ.send(handler, errorMessage, ZMQ.ZMQ_SNDMORE);
                        assertThat(ret, is(errorMessage.length()));
                        ret = ZMQ.send(handler, "", ZMQ.ZMQ_SNDMORE);
                        assertThat(ret, is(0));
                    }
                    ret = ZMQ.send(handler, "", 0);
                    assertThat(ret, is(0));
                    if (! zapFuture.isDone()) {
                        zapFuture.complete(authentified);
                    }
                }
                else {
                    if (! zapFuture.isDone()) {
                        zapFuture.complete(null);
                    }
                    break;
                }
            }
        }
        catch (Exception | AssertionError ex) {
            ex.printStackTrace();
            zapFuture.completeExceptionally(ex);
        }
       finally {
            ZMQ.closeZeroLinger(handler);
        }
    }
}
