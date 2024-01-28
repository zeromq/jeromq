package org.zeromq.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The ZDigest class generates a hash from chunks of data. The current algorithm is SHA-1, chosen for speed.
 */
public class ZDigest
{
    private final byte[] buffer;

    private final MessageDigest sha1;

    /**
     * Creates a new digester.
     */
    public ZDigest()
    {
        this(new byte[8192]);
    }

    /**
     * Creates a new digester.
     * @param buffer the temp buffer used for computation of streams.
     */
    public ZDigest(byte[] buffer)
    {
        this.buffer = buffer;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public ZDigest update(InputStream input) throws IOException
    {
        int read = input.read(buffer);

        while (read != -1) {
            sha1.update(buffer, 0, read);
            read = input.read(buffer);
        }
        return this;
    }

    public ZDigest update(byte[] input)
    {
        return update(input, 0, input.length);
    }

    public ZDigest update(byte[] input, int offset, int length)
    {
        sha1.update(input, offset, length);
        return this;
    }

    public byte[] data()
    {
        return sha1.digest();
    }

    public int size()
    {
        return sha1.digest().length;
    }

    public String string()
    {
        return ZData.toString(data());
    }
}
