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
    private static final boolean VERBOSE_MODE       = false;
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
            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            auth.configurePlain("*", PASSWORDS_FILE);

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setAsServerPlain(true);
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

            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            auth.configureCurve(ZAuth.CURVE_ALLOW_ANY);

            //  We need two certificates, one for the client and one for
            //  the server. The client must know the server's public key
            //  to make a CURVE connection.
            ZCert clientCert = new ZCert();
            ZCert serverCert = new ZCert();

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setZapDomain("global".getBytes());
            server.setAsServerCurve(true);
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

            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
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
            server.setAsServerCurve(true);
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
    public void testCurveFail() throws IOException
    {
        System.out.println("testCurveFail");
        // this is the same test but here we do not save the client's certificate into the certstore's folder
        final ZContext ctx = new ZContext();
        try {
            ZAuth auth = new ZAuth(ctx, new ZCertStore.Hasher());
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            // auth send the replies
            auth.replies(true);

            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
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
            server.setAsServerCurve(true);
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
