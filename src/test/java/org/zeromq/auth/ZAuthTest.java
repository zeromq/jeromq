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
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Test are basically the java-ports based on <a href="http://hintjens.com/blog:49">Using ZeroMQ Security (part 2)</a>
 * @author thomas (dot) trocha (at) gmail (dot) com
 *
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
        //  Create context
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            auth.configurePlain("*", PASSWORDS_FILE);
            // Make sure the ZAuthAgent's command to configure plain-mechanism got through before the sockets start working...

            //  Create and bind server socket
            ZMQ.Socket server = ctx.createSocket(ZMQ.PUSH);
            server.setAsServerPlain(true);
            server.setZapDomain("global".getBytes());
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            client.setPlainUsername("admin".getBytes());
            client.setPlainPassword("secret".getBytes());
            client.connect("tcp://127.0.0.1:" + port);

            // added some timeouts to prevent blocking during tests
            client.setReceiveTimeOut(100);
            server.setSendTimeOut(100);

            //  Send a single message from server to client
            server.send("Hello");
            String message = client.recvStr(0);

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
        // accept any client-certificate

        //  Create context
        final ZContext ctx = new ZContext();
        try {
            //

            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            auth.configureCurve(ZAuth.CURVE_ALLOW_ANY);
            // Make sure the ZAuthAgent's command to configure plain-mechanism got through before the sockets start working...

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
            client.connect("tcp://127.0.0.1:" + port);

            // added some timeouts to prevent blocking during tests
            client.setReceiveTimeOut(100);
            server.setSendTimeOut(100);

            //  Send a single message from server to client
            boolean sendSuccessful = server.send("Hello");
            assertThat(sendSuccessful, is(true));

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
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(CERTIFICATE_FOLDER);
            // Make sure the ZAuthAgent's command to configure plain-mechanism got through before the sockets start working...
            // This usually works without sleeping, but there is a chance the command is not handled before
            // the sockets read/write and then it will throw an error cause the configuration didn't run already
            //            auth.syncConfig();

            //  We'll generate a new client certificate and save the public part
            //  in the certificate store (in practice this would be done by hand
            //  or some out-of-band process).
            ZCert clientCert = new ZCert();
            clientCert.setMeta("name", "Client test certificate");
            clientCert.setMeta("meta1/meta2/meta3", "Third level of meta");
            // wait a second before overwriting a cert, otherwise the certstore won't see that the file actually changed and will deny
            // if creating new files, this is not needed

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
            client.connect("tcp://127.0.0.1:" + port);

            // added some timeouts to prevent blocking during tests
            client.setReceiveTimeOut(100);
            server.setSendTimeOut(100);

            //  Send a single message from server to client
            boolean sendSuccessful = server.send("Hello");
            assertThat(sendSuccessful, is(true));

            String message = client.recvStr(0);
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
        // this is the same test but here we do not save the client's certificate into the certstore's folder
        final ZContext ctx = new ZContext();
        try {
            //  Start an authentication engine for this context. This engine
            //  allows or denies incoming connections (talking to the libzmq
            //  core over a protocol called ZAP).
            ZAuth auth = new ZAuth(ctx);
            //  Get some indication of what the authenticator is deciding
            auth.setVerbose(VERBOSE_MODE);
            //  Whitelist our address; any other address will be rejected
            auth.allow("127.0.0.1");
            //  Tell authenticator to use the certificate store in .curve
            auth.configureCurve(CERTIFICATE_FOLDER);
            // Make sure the ZAuthAgent's command to configure plain-mechanism got through before the sockets start working...

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
            server.setCurvePublicKey(serverCert.getPublicKey());
            server.setCurveSecretKey(serverCert.getSecretKey());
            final int port = server.bindToRandomPort("tcp://*");
            // added timeout to prevent blocking during tests
            server.setSendTimeOut(100);

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(ZMQ.PULL);
            client.setCurvePublicKey(clientCert.getPublicKey());
            client.setCurveSecretKey(clientCert.getSecretKey());
            client.setCurveServerKey(serverCert.getPublicKey());
            client.connect("tcp://127.0.0.1:" + port);
            // add a timeout so that the client won't wait forever (since it is not connected)
            client.setReceiveTimeOut(100);

            //  Send a single message from server to client
            boolean sendSuccessful = server.send("Hello");
            assertThat(sendSuccessful, is(true));
            // the timeout will leave the recvStr-method with null as result
            String message = client.recvStr(0);
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
}
