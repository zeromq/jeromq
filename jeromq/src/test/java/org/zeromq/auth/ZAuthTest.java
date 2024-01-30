package org.zeromq.auth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zeromq.SocketType;
import org.zeromq.ZAuth;
import org.zeromq.ZCert;
import org.zeromq.ZCertStore;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.FileWriter;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test are basically the java-ports based on <a href="http://hintjens.com/blog:49">Using ZeroMQ Security (part 2)</a>
 */
public class ZAuthTest
{
    private static final boolean VERBOSE_MODE = true;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private String  certificateFolder;
    private String  passwordsFile;

    @Before
    public void init() throws IOException
    {
        // create test-passwords
        passwordsFile = folder.newFile().getPath();
        certificateFolder = folder.newFolder().getPath();
        try (FileWriter write = new FileWriter(passwordsFile)) {
            write.write("guest=guest\n");
            write.write("tourist=1234\n");
            write.write("admin=secret\n");
        }
    }

    @Test(timeout = 5000)
    public void testNull()
    {
        try (ZContext ctx = new ZContext();
            ZAuth auth = new ZAuth(ctx)) {
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
            server.setZapDomain("test");
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));

            String message = client.recvStr();

            assertThat(message, is("Hello"));
        }
    }

    @Test(timeout = 5000)
    public void testNullAllowed()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx)) {
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
            server.setZapDomain("test");
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));

            String message = client.recvStr();

            assertThat(message, is("Hello"));
        }
    }

    @Test(timeout = 5000)
    public void testNullWithNoDomain()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx)) {
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(1000);
            assertThat(reply, nullValue());

            String message = client.recvStr();

            assertThat(message, is("Hello"));
        }
    }

    @Test(timeout = 5000)
    public void testPlainWithPassword()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.configurePlain("*", passwordsFile);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
            server.setPlainServer(true);
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("secret".getBytes());
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is("admin"));

            String message = client.recvStr();

            assertThat(message, is("Hello"));
        }
    }

    @Test(timeout = 5000)
    public void testPlainWithPasswordDenied()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx);
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(true);
            // auth send the replies
            auth.replies(true);
            auth.configurePlain("*", passwordsFile);

            //  Create and bind server socket
            server.setPlainServer(true);
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("wrong".getBytes());

            //  Create and connect client socket
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(400));
        }
    }

    @Test(timeout = 5000)
    public void testCurveAnyClient()
    {
        // accept any client-certificate
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            auth.configureCurve(ZAuth.CURVE_ALLOW_ANY);

            //  We need two certificates, one for the client and one for
            //  the server. The client must know the server's public key
            //  to make a CURVE connection.
            ZCert clientCert = new ZCert();
            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            serverCert.apply(server);
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            clientCert.apply(client);
            client.setCurveServerKey(serverCert.getPublicKey());
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));

            System.out.println("ZAuth replied " + reply);

            String message = client.recvStr();
            assertThat(message, is("Hello"));
        }

    }

    @Test(timeout = 5000)
    public void testCurveSuccessful() throws IOException
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(certificateFolder);

            //  We'll generate a new client certificate and save the public part
            //  in the certificate store (in practice this would be done by hand
            //  or some out-of-band process).
            ZCert clientCert = new ZCert();
            clientCert.setMeta("name", "Client test certificate");
            clientCert.setMeta("meta1/meta2/meta3", "Third level of meta");
            // wait a second before overwriting a cert, otherwise the certstore won't see that the file actually changed and will deny
            // if creating new files, this is not needed
            //            ZMQ.sleep(1);
            clientCert.savePublic(certificateFolder + "/testcert.pub");

            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            server.setCurvePublicKey(serverCert.getPublicKey());
            server.setCurveSecretKey(serverCert.getSecretKey());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            client.setCurvePublicKey(clientCert.getPublicKey());
            client.setCurveSecretKey(clientCert.getSecretKey());
            client.setCurveServerKey(serverCert.getPublicKey());
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(clientCert.getPublicKeyAsZ85()));

            String message = client.recvStr();
            assertThat(message, is("Hello"));
        }
    }

    @Test(timeout = 5000)
    public void testBlacklistDenied()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Timestamper());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Blacklist our address
            auth.deny("127.0.0.1");

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));
            assertThat(reply.userId, is(""));
        }
    }

    @Test(timeout = 5000)
    public void testBlacklistAllowed()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Blacklist another address
            auth.deny("127.0.0.2");

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));
        }
    }

    @Test(timeout = 5000)
    public void testWhitelistDenied()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Whitelist another address
            auth.allow("127.0.0.2");

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));
            assertThat(reply.userId, is(""));
        }
    }

    @Test(timeout = 5000)
    public void testWhitelistAllowed()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).

            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Whitelist our address
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));
        }
    }

    @Test(timeout = 5000)
    public void testCurveFail()
    {
        // this is the same test but here we do not save the client's certificate into the certstore's folder
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Timestamper());
             ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
             ZMQ.Socket client = ctx.createSocket(SocketType.PULL)) {
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(certificateFolder);

            //  We'll generate a new client certificate and save the public part
            //  in the certificate store (in practice this would be done by hand
            //  or some out-of-band process).
            ZCert clientCert = new ZCert();
            clientCert.setMeta("name", "Client test certificate");

            // HERE IS THE PROBLEM. Not client-certificate means that the client will be rejected
            // client_cert.savePublic(CERTIFICATE_FOLDER+"/testcert.pub");

            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            serverCert.apply(server);
            boolean rc = server.bind("tcp://localhost:*");
            assertThat(rc, is(true));

            //  Create and connect client socket
            clientCert.apply(client);
            client.setCurveServerKey(serverCert.getPublicKey());
            rc = client.connect(server.getLastEndpoint());
            assertThat(rc, is(true));

            // add a timeout so that the client won't wait forever (since it is not connected)
            rc = client.setReceiveTimeOut(100);
            assertThat(rc, is(true));

            //  By default PUSH sockets block if there's no peer
            rc = server.setSendTimeOut(200);
            assertThat(rc, is(true));
            //  Send a single message from server to client
            server.send("Hello");

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));

            // the timeout will leave the recvStr-method with null as result
            String message = client.recvStr();
            assertThat(message, nullValue());
        }
    }

    @Test(timeout = 5000)
    public void testNoReplies()
    {
        try (ZContext ctx = new ZContext();
             ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher())) {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth do not send the replies
            auth.replies(false);

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply, nullValue());

            reply = auth.nextReply(1);
            assertThat(reply, nullValue());
        }
    }
}
