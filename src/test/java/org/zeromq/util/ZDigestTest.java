package org.zeromq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class ZDigestTest
{
    @Test
    public void testData()
    {
        byte[] buf = new byte[1024];
        Arrays.fill(buf, (byte) 0xAA);

        ZDigest digest = new ZDigest();

        digest.update(buf);
        byte[] data = digest.data();
        assertThat(byt(data[0]), is(0xDE));
        assertThat(byt(data[1]), is(0xB2));
        assertThat(byt(data[2]), is(0x38));
        assertThat(byt(data[3]), is(0x07));
    }

    @Test
    public void testSize()
    {
        byte[] buf = new byte[1024];
        Arrays.fill(buf, (byte) 0xAA);

        ZDigest digest = new ZDigest();

        digest.update(buf);
        int size = digest.size();
        assertThat(size, is(20));
    }

    private int byt(byte data)
    {
        return data & 0xff;
    }

    @Test
    public void testString()
    {
        byte[] buf = new byte[1024];
        Arrays.fill(buf, (byte) 0xAA);

        ZDigest digest = new ZDigest();

        digest.update(buf);
        String string = digest.string();
        assertThat(string, is("DEB23807D4FE025E900FE9A9C7D8410C3DDE9671"));
    }
}
