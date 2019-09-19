package org.zeromq;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.zeromq.util.ZDigest;
import org.zeromq.util.ZMetadata;

/**
 *
    To authenticate new clients using the ZeroMQ CURVE security mechanism,
    we have to check that the client's public key matches a key we know and
    accept. There are numerous ways to store accepted client public keys.
    The mechanism CZMQ implements is "certificates" (plain text files) held
    in a "certificate store" (a disk directory). This class works with such
    certificate stores, and lets you easily load them from disk, and check
    if a given client public key is known or not. The {@link org.zeromq.ZCert} class does the
    work of managing a single certificate.
 * <p>Those files need to be in ZMP-Format which is created by {@link org.zeromq.ZConfig}</p>
 */
public class ZCertStore
{
    public interface Fingerprinter
    {
        byte[] print(File path);
    }

    public static final class Timestamper implements Fingerprinter
    {
        private final byte[] buf = new byte[Long.SIZE / Byte.SIZE];

        @Override
        public byte[] print(File path)
        {
            final long value = path.lastModified();
            buf[0] = (byte) ((value >>> 56) & 0xff);
            buf[0] = (byte) ((value >>> 48) & 0xff);
            buf[0] = (byte) ((value >>> 40) & 0xff);
            buf[0] = (byte) ((value >>> 32) & 0xff);
            buf[0] = (byte) ((value >>> 24) & 0xff);
            buf[0] = (byte) ((value >>> 16) & 0xff);
            buf[0] = (byte) ((value >>> 8) & 0xff);
            buf[0] = (byte) ((value) & 0xff);
            return buf;
        }
    }

    public static final class Hasher implements Fingerprinter
    {
        // temporary buffer used for digest. Instance member for performance reasons.
        private final byte[] buffer = new byte[8192];

        @Override
        public byte[] print(File path)
        {
            InputStream input = stream(path);
            if (input != null) {
                try {
                    return new ZDigest(buffer).update(input).data();
                }
                catch (IOException e) {
                    return null;
                }
                finally {
                    try {
                        input.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        private InputStream stream(File path)
        {
            if (path.isFile()) {
                try {
                    return new FileInputStream(path);
                }
                catch (FileNotFoundException e) {
                    return null;
                }
            }
            else if (path.isDirectory()) {
                List<String> list = Arrays.asList(path.list());
                Collections.sort(list);
                return new ByteArrayInputStream(list.toString().getBytes(ZMQ.CHARSET));
            }
            return null;
        }
    }

    private interface IFileVisitor
    {
        /**
         * Visits a file.
         * @param file the file to visit.
         * @return true to stop the traversal, false to continue.
         */
        boolean visitFile(File file);

        /**
         * Visits a directory.
         * @param dir the directory to visit.
         * @return true to stop the traversal, false to continue.
         */
        boolean visitDir(File dir);
    }

    //  Directory location
    private final File location;

    // the scanned files (and directories) along with their fingerprint
    private final Map<File, byte[]> fingerprints = new HashMap<>();

    // collected public keys
    private final Map<String, ZMetadata> publicKeys = new HashMap<>();

    private final Fingerprinter finger;

    /**
     * Create a Certificate Store at that file system folder location
     * @param location
     */
    public ZCertStore(String location)
    {
        this(location, new Timestamper());
    }

    public ZCertStore(String location, Fingerprinter fingerprinter)
    {
        this.finger = fingerprinter;
        this.location = new File(location);
        loadFiles();
    }

    private boolean traverseDirectory(File root, IFileVisitor visitor)
    {
        assert (root.exists());
        assert (root.isDirectory());

        if (visitor.visitDir(root)) {
            return true;
        }
        for (File file : root.listFiles()) {
            if (file.isFile()) {
                if (visitor.visitFile(file)) {
                    return true;
                }
            }
            else if (file.isDirectory()) {
                return traverseDirectory(file, visitor);
            }
            else {
                System.out.printf(
                                  "WARNING: %s is neither file nor directory? This shouldn't happen....SKIPPING%n",
                                  file.getAbsolutePath());
            }
        }
        return false;
    }

    /**
     * Check if a public key is in the certificate store.
     * @param publicKey needs to be a 32 byte array representing the public key
     */
    public boolean containsPublicKey(byte[] publicKey)
    {
        Utils.checkArgument(
                            publicKey.length == 32,
                            "publickey needs to have a size of 32 bytes. got only " + publicKey.length);
        return containsPublicKey(ZMQ.Curve.z85Encode(publicKey));
    }

    /**
     * check if a z85-based public key is in the certificate store.
     * This method will scan the folder for changes on every call
     * @param publicKey
     */
    public boolean containsPublicKey(String publicKey)
    {
        Utils.checkArgument(
                            publicKey.length() == 40,
                            "z85 publickeys should have a length of 40 bytes but got " + publicKey.length());
        reloadIfNecessary();
        return publicKeys.containsKey(publicKey);
    }

    public ZMetadata getMetadata(String publicKey)
    {
        reloadIfNecessary();
        return publicKeys.get(publicKey);
    }

    private void loadFiles()
    {
        final Map<String, ZMetadata> keys = new HashMap<>();
        if (!location.exists()) {
            location.mkdirs();
        }
        final Map<File, byte[]> collected = new HashMap<>();

        traverseDirectory(location, new IFileVisitor()
        {
            @Override
            public boolean visitFile(File file)
            {
                try {
                    ZConfig zconf = ZConfig.load(file.getAbsolutePath());
                    String publicKey = zconf.getValue("curve/public-key");
                    if (publicKey == null) {
                        System.out.printf(
                                          "Warning!! File %s has no curve/public-key-element. SKIPPING!%n",
                                          file.getAbsolutePath());
                        return false;
                    }
                    if (publicKey.length() == 32) { // we want to store the public-key as Z85-String
                        publicKey = ZMQ.Curve.z85Encode(publicKey.getBytes(ZMQ.CHARSET));
                    }
                    assert (publicKey.length() == 40);
                    keys.put(publicKey, ZMetadata.read(zconf));
                    collected.put(file, finger.print(file));
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public boolean visitDir(File dir)
            {
                collected.put(dir, finger.print(dir));
                return false;
            }
        });

        publicKeys.clear();
        publicKeys.putAll(keys);
        fingerprints.clear();
        fingerprints.putAll(collected);
    }

    int getCertificatesCount()
    {
        reloadIfNecessary();
        return publicKeys.size();
    }

    boolean reloadIfNecessary()
    {
        if (checkForChanges()) {
            loadFiles();
            return true;
        }
        return false;
    }

    /**
     * Check if files in the certificate folders have been added or removed.
     */
    boolean checkForChanges()
    {
        // initialize with last checked files
        final Map<File, byte[]> presents = new HashMap<>(fingerprints);
        boolean modified = traverseDirectory(location, new IFileVisitor()
        {
            @Override
            public boolean visitFile(File file)
            {
                return modified(presents.remove(file), file);
            }

            @Override
            public boolean visitDir(File dir)
            {
                return modified(presents.remove(dir), dir);
            }
        });
        // if some files remain, that means they have been deleted since last scan
        return modified || !presents.isEmpty();
    }

    private boolean modified(byte[] fingerprint, File path)
    {
        if (!path.exists()) {
            // run load-files if one file is not present
            return true;
        }
        if (fingerprint == null) {
            // file was not scanned before, it has been added
            return true;
        }
        // true if file has been modified.
        return !Arrays.equals(fingerprint, finger.print(path));
    }
}
