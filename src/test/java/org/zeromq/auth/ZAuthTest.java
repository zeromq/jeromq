package org.zeromq.auth;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZAuth;
import org.zeromq.ZCert;
import org.zeromq.ZCertStore;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Test are basically the java-ports based on <a href="http://hintjens.com/blog:49">Using ZeroMQ Security (part 2)</a>
 */
public class ZAuthTest
{
    private static final boolean VERBOSE_MODE       = true;
    private static final String  PASSWORDS_FILE     = "target/passwords";
    private static final String  CERTIFICATE_FOLDER = "target/curve";

    @Before
    public void init()
    {
        // create test-passwords
        try {
            FileWriter write = new FileWriter(PASSWORDS_FILE);
            write.write("guest=guest\n");
            write.write("tourist=1234\n");
            write.write("admin=secret\n");
            write.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNull() throws IOException
    {
        System.out.println("testNull");
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("test");
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));

            String message = client.recvStr();

            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testNullAllowed() throws IOException
    {
        System.out.println("testNullAllowed");
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("test");
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));

            String message = client.recvStr();

            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testNullWithNoDomain() throws IOException
    {
        System.out.println("testNullWithNoDomain");
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            //            server.setZapDomain("test"); // no domain, so no NULL authentication mechanism
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(1000);
            assertThat(reply, nullValue());

            String message = client.recvStr();

            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testPlainWithPassword() throws IOException
    {
        System.out.println("testPlainWithPassword");
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);
            auth.configurePlain("*", PASSWORDS_FILE);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setPlainServer(true);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("secret".getBytes());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is("admin"));

            String message = client.recvStr();

            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testPlainWithPasswordDenied() throws IOException
    {
        System.out.println("testPlainWithPasswordDenied");
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(true);
            // auth send the replies
            auth.replies(true);
            auth.configurePlain("*", PASSWORDS_FILE);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setPlainServer(true);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("wrong".getBytes());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(400));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testCurveAnyClient() throws IOException
    {
        System.out.println("testCurveAnyClient");
        // accept any client-certificate

        //  Create context
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());

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
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            serverCert.apply(server);
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            clientCert.apply(client);
            client.setCurveServerKey(serverCert.getPublicKey());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply();
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));

            System.out.println("ZAuth replied " + reply.toString());

            String message = client.recvStr();
            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
        }

    }

    @Test
    public void testCurveSuccessful() throws IOException
    {
        System.out.println("testCurveSuccessful");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(CERTIFICATE_FOLDER);

            //  We'll generate a new client certificate and save the public part
            //  in the certificate store (in practice this would be done by hand
            //  or some out-of-band process).
            ZCert clientCert = new ZCert();
            clientCert.setMeta("name", "Client test certificate");
            clientCert.setMeta("meta1/meta2/meta3", "Third level of meta");
            // wait a second before overwriting a cert, otherwise the certstore won't see that the file actually changed and will deny
            // if creating new files, this is not needed
            //            ZMQ.sleep(1);
            clientCert.savePublic(CERTIFICATE_FOLDER + "/testcert.pub");

            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            server.setCurvePublicKey(serverCert.getPublicKey());
            server.setCurveSecretKey(serverCert.getSecretKey());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            client.setCurvePublicKey(clientCert.getPublicKey());
            client.setCurveSecretKey(clientCert.getSecretKey());
            client.setCurveServerKey(serverCert.getPublicKey());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(clientCert.getPublicKeyAsZ85()));

            String message = client.recvStr();
            assertThat(message, is("Hello"));

            auth.close();
        }
        finally {
            ctx.close();
            TestUtils.cleanupDir(CERTIFICATE_FOLDER);
        }
    }

    @Test
    public void testBlacklistDenied() throws IOException
    {
        System.out.println("testBlacklistDenied");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Timestamper());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Blacklist our address
            auth.deny("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));
            assertThat(reply.userId, is(""));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testBlacklistAllowed() throws IOException
    {
        System.out.println("testBlacklistAllowed");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Blacklist another address
            auth.deny("127.0.0.2");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testWhitelistDenied() throws IOException
    {
        System.out.println("testWhitelistDenied");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Whitelist another address
            auth.allow("127.0.0.2");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));
            assertThat(reply.userId, is(""));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testWhitelistAllowed() throws IOException
    {
        System.out.println("testWhitelistAllowed");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Whitelist our address
            auth.allow("127.0.0.1");

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(200));
            assertThat(reply.userId, is(""));

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testCurveFail() throws IOException
    {
        System.out.println("testCurveFail");
        // this is the same test but here we do not save the client's certificate into the certstore's folder
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Timestamper());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(CERTIFICATE_FOLDER);

            //  We'll generate a new client certificate and save the public part
            //  in the certificate store (in practice this would be done by hand
            //  or some out-of-band process).
            ZCert clientCert = new ZCert();
            clientCert.setMeta("name", "Client test certificate");

            // HERE IS THE PROBLEM. Not client-certificate means that the client will be rejected
            // client_cert.savePublic(CERTIFICATE_FOLDER+"/testcert.pub");

            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            server.setCurveServer(true);
            serverCert.apply(server);
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            clientCert.apply(client);
            client.setCurveServerKey(serverCert.getPublicKey());
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            // add a timeout so that the client won't wait forever (since it is not connected)
            rc = client.setReceiveTimeOut(100);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply.statusCode, is(400));

            // the timeout will leave the recvStr-method with null as result
            String message = client.recvStr();
            assertThat(message, nullValue());

            auth.close();
        }
        finally {
            ctx.close();
            TestUtils.cleanupDir(CERTIFICATE_FOLDER);
        }
    }

    @Test
    public void testNoReplies() throws IOException
    {
        System.out.println("testNoReplies");
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth do not send the replies
            auth.replies(false);

            ZAuth.ZapReply reply = auth.nextReply(true);
            assertThat(reply, nullValue());

            reply = auth.nextReply(1);
            assertThat(reply, nullValue());

            auth.close();
        }
        finally {
            ctx.close();
        }
    }

    @After
    public void cleanup()
    {
        File deletePasswords = new File(PASSWORDS_FILE);
        deletePasswords.delete();
    }

    //    @Test
    public void testRepeated() throws IOException
    {
        for (int idx = 0; idx < 10000; ++idx) {
            System.out.println("+++++ " + idx);
            testCurveSuccessful();
            testCurveFail();
            testPlainWithPassword();
            testCurveAnyClient();
        }
    }
}
