package org.zeromq;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.zeromq.ZMQ.Curve;
import org.zeromq.ZMQ.Curve.KeyPair;
import org.zeromq.util.ZMetadata;

/**
 *
    The ZCert class provides a way to create and work with security
    certificates for the ZMQ CURVE mechanism. A certificate contains a
    public + secret key pair, plus metadata. It can be used as a
    temporary object in memory, or persisted to disk.
    <p>
    To exchange certificates, send the public file via some secure route.
    Certificates are not signed but are text files that can be verified by
    eye.
    <p>
    Certificates are stored in the <a href="https://rfc.zeromq.org/spec:4/ZPL/">ZeroMQ Property Language format</a>.
    <br>
    They have two sections, "metadata" and "curve".
    <br>
    The first contains a list of 'name = value' pairs, one per line.
    Values may be enclosed in quotes.
    <br>
    The curve section has a 'public-key = key-value' and, for secret certificates, a
    'secret-key = key-value' line.
    <br>
    The key-value is a {@link zmq.util.Z85 Z85-encoded CURVE key}.
 *
 */
public class ZCert
{
    private final byte[]    publicKey;                  //  Public key in binary
    private final byte[]    secretKey;                  //  Secret key in binary
    private final String    publicTxt;                  //  Public key in Z85 text
    private final String    secretTxt;                  //  Secret key in Z85 text
    private final ZMetadata metadata = new ZMetadata(); //  Certificate metadata

    public ZCert()
    {
        this(ZMQ.Curve.generateKeyPair());
    }

    public ZCert(String publicKey)
    {
        this(publicKey, null);
    }

    public ZCert(KeyPair keypair)
    {
        this(keypair.publicKey, keypair.secretKey);
    }

    public ZCert(byte[] publicKey, byte[] secretKey)
    {
        Utils.checkArgument(publicKey != null, "Public key has to be provided for a ZCert");
        assertKey(publicKey.length, Curve.KEY_SIZE, "Public");
        if (secretKey != null) {
            assertKey(secretKey.length, Curve.KEY_SIZE, "Secret");
        }
        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        this.publicTxt = Curve.z85Encode(this.publicKey);

        if (secretKey == null) {
            this.secretKey = null;
            this.secretTxt = null;
        }
        else {
            this.secretKey = Arrays.copyOf(secretKey, secretKey.length);
            this.secretTxt = Curve.z85Encode(this.secretKey);
        }
    }

    public ZCert(String publicKey, String secretKey)
    {
        Utils.checkArgument(publicKey != null, "Public key has to be provided for a ZCert");
        assertKey(publicKey.length(), Curve.KEY_SIZE_Z85, "Public");
        if (secretKey != null) {
            assertKey(secretKey.length(), Curve.KEY_SIZE_Z85, "Secret");
        }
        this.publicKey = Curve.z85Decode(publicKey);
        this.publicTxt = publicKey;

        if (secretKey == null) {
            this.secretKey = null;
            this.secretTxt = null;
        }
        else {
            this.secretKey = Curve.z85Decode(secretKey);
            this.secretTxt = secretKey;
        }
    }

    private void assertKey(int length, int expected, String flavour)
    {
        Utils.checkArgument(length == expected, flavour + " key shall have a size of " + expected);
    }

    public byte[] getPublicKey()
    {
        return publicKey;
    }

    public byte[] getSecretKey()
    {
        return secretKey;
    }

    public String getPublicKeyAsZ85()
    {
        return publicTxt;
    }

    public String getSecretKeyAsZ85()
    {
        return secretTxt;
    }

    public void apply(ZMQ.Socket socket)
    {
        socket.setCurvePublicKey(publicKey);
        socket.setCurveSecretKey(secretKey);
    }

    public ZMetadata getMetadata()
    {
        return metadata;
    }

    public void setMeta(String key, String value)
    {
        metadata.set(key, value);
    }

    public void unsetMeta(String key)
    {
        metadata.remove(key);
    }

    public String getMeta(String key)
    {
        return metadata.get(key);
    }

    private void add(ZMetadata meta, ZConfig config)
    {
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date());
        config.addComment(String.format("** Generated on %1$s by ZCert **", now));
        for (String key : meta.keySet()) {
            config.putValue("metadata/" + key, meta.get(key));
        }
    }

    /**
     * Saves the public key to a file.
     * <p>
     * <strong>This method will overwrite contents of existing file</strong>
     * @param filename the path of the file to save the certificate into.
     * @return the saved file or null if dumped to the standard output
     * @throws IOException if unable to save the file.
     */
    public File savePublic(String filename) throws IOException
    {
        return publicConfig().save(filename);
    }

    /**
     * Saves the public key to a writer.
     * @param writer the writer to save the certificate into.
     * @throws IOException if unable to dump the public configuration.
     */
    public void savePublic(Writer writer) throws IOException
    {
        publicConfig().save(writer);
    }

    private ZConfig publicConfig()
    {
        ZConfig conf = new ZConfig("root", null);
        add(metadata, conf);
        conf.addComment("   ZeroMQ CURVE Public Certificate");
        conf.addComment("   Exchange securely, or use a secure mechanism to verify the contents");
        conf.addComment("   of this file after exchange. Store public certificates in your home");
        conf.addComment("   directory, in the .curve subdirectory.");
        conf.putValue("/curve/public-key", publicTxt);
        return conf;
    }

    /**
     * Saves the public and secret keys to a file.
     * <p>
     * <strong>This method will overwrite contents of existing file</strong>
     * @param filename the path of the file to save the certificate into.
     * @return the saved file or null if dumped to the standard output
     * @throws IOException if unable to save the file.
     */
    public File saveSecret(String filename) throws IOException
    {
        return secretConfig().save(filename);
    }

    /**
     * Saves the public and secret keys to a writer.
     * @param writer the writer to save the certificate into.
     * @throws IOException if unable to dump the configuration.
     */
    public void saveSecret(Writer writer) throws IOException
    {
        secretConfig().save(writer);
    }

    private ZConfig secretConfig()
    {
        ZConfig conf = new ZConfig("root", null);
        add(metadata, conf);
        conf.addComment("   ZeroMQ CURVE **Secret** Certificate");
        conf.addComment("   DO NOT PROVIDE THIS FILE TO OTHER USERS nor change its permissions.");
        conf.putValue("/curve/public-key", publicTxt);
        conf.putValue("/curve/secret-key", secretTxt);
        return conf;
    }
}
