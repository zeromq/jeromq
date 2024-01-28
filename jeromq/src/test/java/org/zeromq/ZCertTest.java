package org.zeromq;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.auth.TestUtils;
import org.zeromq.util.ZMetadata;
import zmq.util.AndroidProblematic;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@AndroidProblematic
public class ZCertTest
{
    private static final String CERT_LOCATION = "target/testCerts";

    @Before
    public void init()
    {
        // first cleanup test-directory if still present
        TestUtils.cleanupDir(CERT_LOCATION);
        File store = new File(CERT_LOCATION);

        store.mkdirs();
    }

    @After
    public void tearDown()
    {
        TestUtils.cleanupDir(CERT_LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullStringPublicKey()
    {
        new ZCert((String) null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullBytesPublicKey()
    {
        new ZCert((byte[]) null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidBytesPublicKey()
    {
        new ZCert(new byte[0], null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidStringPublicKey()
    {
        new ZCert("", null);
    }

    @Test
    public void testConstructorValidPublicKeyZ85()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        assertThat(keyPair.publicKey.length(), is(40));
        ZCert cert = new ZCert(keyPair.publicKey);

        assertThat(cert.getPublicKeyAsZ85(), is(keyPair.publicKey));
        assertThat(cert.getPublicKey(), is(ZMQ.Curve.z85Decode(keyPair.publicKey)));
        assertThat(cert.getSecretKeyAsZ85(), nullValue());
        assertThat(cert.getSecretKey(), nullValue());
    }

    @Test
    public void testConstructorValidPublicKey()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        byte[] bytes = ZMQ.Curve.z85Decode(keyPair.publicKey);

        ZCert cert = new ZCert(bytes, null);
        assertThat(cert.getPublicKeyAsZ85(), is(keyPair.publicKey));
        assertThat(cert.getPublicKey(), is(bytes));
        assertThat(cert.getSecretKeyAsZ85(), nullValue());
        assertThat(cert.getSecretKey(), nullValue());
    }

    @Test
    public void testConstructorValidKeysZ85()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        assertThat(keyPair.publicKey.length(), is(40));
        ZCert cert = new ZCert(keyPair.publicKey, keyPair.secretKey);

        assertThat(cert.getPublicKeyAsZ85(), is(keyPair.publicKey));
        assertThat(cert.getSecretKeyAsZ85(), is(keyPair.secretKey));
        assertThat(cert.getPublicKey(), is(ZMQ.Curve.z85Decode(keyPair.publicKey)));
        assertThat(cert.getSecretKey(), is(ZMQ.Curve.z85Decode(keyPair.secretKey)));
    }

    @Test
    public void testConstructorValidKeys()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        byte[] bytes = ZMQ.Curve.z85Decode(keyPair.publicKey);
        byte[] secret = ZMQ.Curve.z85Decode(keyPair.secretKey);

        ZCert cert = new ZCert(bytes, secret);
        assertThat(cert.getPublicKeyAsZ85(), is(keyPair.publicKey));
        assertThat(cert.getSecretKeyAsZ85(), is(keyPair.secretKey));
        assertThat(cert.getPublicKey(), is(bytes));
        assertThat(cert.getSecretKey(), is(secret));
    }

    @Test
    public void testSetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        String version = cert.getMeta("version");
        assertThat(version, is("1"));

        cert.setMeta("version", "2");
        version = cert.getMeta("version");
        assertThat(version, is("2"));
    }

    @Test
    public void testGetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        ZMetadata meta = cert.getMetadata();
        String version = meta.get("version");
        assertThat(version, is("1"));

        meta.set("version", "2");
        version = cert.getMeta("version");
        assertThat(version, is("2"));
    }

    @Test
    public void testUnsetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        cert.unsetMeta("version");
        assertThat(cert.getMeta("version"), nullValue());
    }

    @Test
    public void testSavePublic() throws IOException
    {
        ZCert cert = new ZCert("uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U", "!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0");
        cert.setMeta("version", "1");

        StringWriter writer = new StringWriter();
        cert.savePublic(writer);

        String datePattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-]?[0-9]{4}";
        String expected = "# \\*\\* Generated on " + datePattern + " by ZCert \\*\\*\n" +
                "#    ZeroMQ CURVE Public Certificate\n" +
                "#    Exchange securely, or use a secure mechanism to verify the contents\n" +
                "#    of this file after exchange. Store public certificates in your home\n" +
                "#    directory, in the .curve subdirectory.\n\n" +
                "metadata\n" +
                "    version = \"1\"\n" +
                "curve\n" +
                "    public-key = \"uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U\"\n";
        String result = writer.toString();
        assertThat(Pattern.compile(expected).matcher(result).matches(), is(true));
    }

    @Test
    public void testSaveSecret() throws IOException
    {
        ZCert cert = new ZCert("uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U", "!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0");
        cert.setMeta("version", "1");

        StringWriter writer = new StringWriter();
        cert.saveSecret(writer);

        String datePattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-]?[0-9]{4}";
        String expected = "# \\*\\* Generated on " + datePattern + " by ZCert \\*\\*\n" +
                "#    ZeroMQ CURVE \\*\\*Secret\\*\\* Certificate\n" +
                "#    DO NOT PROVIDE THIS FILE TO OTHER USERS nor change its permissions.\n\n" +
                "metadata\n" +
                "    version = \"1\"\n" +
                "curve\n" +
                "    secret-key = \"!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0\"\n" +
                "    public-key = \"uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U\"\n";
        String result = writer.toString();

        assertThat(Pattern.compile(expected).matcher(result).matches(), is(true));
    }

    @Test
    public void testSavePublicFile() throws IOException
    {
        ZCert cert = new ZCert();
        cert.savePublic(CERT_LOCATION + "/test.cert");
        File file = new File(CERT_LOCATION + "/test.cert");
        assertThat(file.exists(), is(true));
    }

    @Test
    public void testSaveSecretFile() throws IOException
    {
        ZCert cert = new ZCert();
        cert.saveSecret(CERT_LOCATION + "/test_secret.cert");
        File file = new File(CERT_LOCATION + "/test_secret.cert");
        assertThat(file.exists(), is(true));
    }
}
