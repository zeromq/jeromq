package org.zeromq;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.auth.TestUtils;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ZCertStoreTest
{
    private ZCertStore certStore;

    private static final String CERTSTORE_LOCATION = "target/testCurveCerts";

    @Before
    public void init()
    {
        // first cleanup test-directory if still present
        TestUtils.cleanupDir(CERTSTORE_LOCATION);

        certStore = new ZCertStore(CERTSTORE_LOCATION, new ZCertStore.Timestamper());
        File store = new File(CERTSTORE_LOCATION);

        // check if the certstore location was created by the certstore
        assertThat(store.exists(), is(true));
        assertThat(store.isDirectory(), is(true));
        // new certstore-directory should have no certs,yet
        assertThat(certStore.getCertificatesCount(), is(0));
    }

    @Test
    public void testAddCertificates() throws IOException
    {
        final int beforeAmount = certStore.getCertificatesCount();
        assertThat(beforeAmount, is(0));

        ZCert c1 = new ZCert();
        File f = c1.savePublic(CERTSTORE_LOCATION + "/c1.cert");
        assertThat(f.exists(), is(true));

        // check the store if something changed, and if yes reload all
        boolean rc = certStore.reloadIfNecessary();
        assertThat(rc, is(true));

        // is now one certificate more in the store?
        assertThat(certStore.getCertificatesCount(), is(1));

        // check if we find our publickey using the Z85-Version to lookup
        assertThat(certStore.containsPublicKey(c1.getPublicKeyAsZ85()), is(true));
        // check if we find our publickey using the binary-Version to lookup (this will internally be encoded to z85 for the lookup)
        assertThat(certStore.containsPublicKey(c1.getPublicKey()), is(true));
        // check if we do not find some random lookup-key. Z85-Keys need to have a length of 40 bytes.
        assertThat(certStore.containsPublicKey("1234567890123456789012345678901234567890"), is(false));

        zmq.ZMQ.msleep(1000);
        // check certs in sub-directories
        ZCert c2 = new ZCert();
        f = c2.savePublic(CERTSTORE_LOCATION + "/sub/c2.cert");
        assertThat(f.exists(), is(true));
        assertThat(certStore.getCertificatesCount(), is(2));
    }

    @Test
    public void testRemoveCertificates() throws IOException
    {
        int beforeAmount = certStore.getCertificatesCount();
        assertThat(beforeAmount, is(0));

        ZCert c1 = new ZCert();
        File f = c1.savePublic(CERTSTORE_LOCATION + "/c1.cert");
        assertThat(f.exists(), is(true));
        // check the store if something changed, and if yes reload all
        boolean rc = certStore.reloadIfNecessary();
        assertThat(rc, is(true));

        // is now one certificate more in the store?
        assertThat(certStore.getCertificatesCount(), is(1));

        File certificate = new File(CERTSTORE_LOCATION + "/c1.cert");
        rc = certificate.exists();
        assertThat(rc, is(true));

        rc = certificate.delete();
        assertThat(rc, is(true));

        rc = certStore.reloadIfNecessary();
        assertThat(rc, is(true));

        assertThat(certStore.getCertificatesCount(), is(0));

        // check if we find our publickey using the Z85-Version to lookup
        assertThat(certStore.containsPublicKey(c1.getPublicKeyAsZ85()), is(false));
        // check if we find our publickey using the binary-Version to lookup (this will internally be encoded to z85 for the lookup)
        assertThat(certStore.containsPublicKey(c1.getPublicKey()), is(false));
    }

    @Test
    public void testcheckForCertificateChanges() throws IOException
    {
        assertThat(certStore.getCertificatesCount(), is(0));

        ZCert cert1 = new ZCert();
        File f = cert1.savePublic(CERTSTORE_LOCATION + "/c1.cert");
        assertThat(f.exists(), is(true));
        ZCert cert2 = new ZCert();
        f = cert2.saveSecret(CERTSTORE_LOCATION + "/sub/c2.cert");
        assertThat(f.exists(), is(true));

        assertThat(certStore.getCertificatesCount(), is(2));
        assertThat(certStore.checkForChanges(), is(false));

        zmq.ZMQ.msleep(1000);
        // rewrite certificates and see if this change gets recognized
        ZCert othercert1 = new ZCert();
        f = othercert1.savePublic(CERTSTORE_LOCATION + "/c1.cert");
        assertThat(f.exists(), is(true));
        // change is recognized if a file is changed only in the main-folder

        assertThat(certStore.checkForChanges(), is(true));
        // second call shall indicate change
        assertThat(certStore.checkForChanges(), is(true));

        // reload the certificates
        assertThat(certStore.getCertificatesCount(), is(2));
        assertThat(certStore.checkForChanges(), is(false));

        assertThat(certStore.containsPublicKey(cert1.getPublicKeyAsZ85()), is(false));
        assertThat(certStore.containsPublicKey(cert1.getPublicKey()), is(false));

        assertThat(certStore.containsPublicKey(othercert1.getPublicKeyAsZ85()), is(true));
        assertThat(certStore.containsPublicKey(othercert1.getPublicKey()), is(true));

        zmq.ZMQ.msleep(1000);
        // check if changes in subfolders get recognized
        f = cert2.savePublic(CERTSTORE_LOCATION + "/sub/c2.cert");
        assertThat(f.exists(), is(true));
        assertThat(certStore.checkForChanges(), is(true));
        // second call shall indicate change
        assertThat(certStore.checkForChanges(), is(true));

        // reload the certificates
        assertThat(certStore.getCertificatesCount(), is(2));
        assertThat(certStore.checkForChanges(), is(false));
    }

    @After
    public void cleanup()
    {
        TestUtils.cleanupDir(CERTSTORE_LOCATION);
    }
}
