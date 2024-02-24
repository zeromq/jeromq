package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

public class WireTest
{
    @Test
    public void testUint16()
    {
        testUint16(0);
        testUint16(0xff);
        testUint16(0xff + 1);
        testUint16(0xff - 1);
        testUint16(0xffff - 1);
        testUint16(0xffff);
    }

    @Test
    public void testUint32()
    {
        testUint32(0);
        testUint32(0xff);
        testUint32(0xff + 1);
        testUint32(0xff - 1);
        testUint32(811233478);
        testUint32(12345678);
        testUint32(2048 * 2000);
        testUint32(2048 * 20000);
        testUint32(2048 * 2048 * 2048);
        testUint32(2048 * 2048 * 2048 * 2048);
        testUint32(2048 * 2048 * 2048 * 2048 * 2048);
        testUint32(2048 * 2048 * 2048 * 2048 * 2048 * 2048);
        testUint32(2048 * 2048 * 2048 * 2048 * 2048 * 2048 * 2048);
        testUint32(2048 * 2048 * 2048 * 2048 * 2048 * 2048 * 2048 * 2048);
        testUint32(Integer.MAX_VALUE);

        testUint32(-811233478);
        testUint32(Integer.MIN_VALUE);
    }

    private void testUint32(int expected)
    {
        ByteBuffer buf = ByteBuffer.allocate(8);
        Wire.putUInt32(buf, expected);
        int actual = Wire.getUInt32(buf);
        assertThat(actual, is(expected));
    }

    private void testUint16(int expected)
    {
        byte[] buf = Wire.putUInt16(expected);
        int actual = Wire.getUInt16(buf);
        assertThat(actual, is(expected));
    }

    @Test
    public void testUnsignedInteger64()
    {
        testUnsignedLong(0);
        testUnsignedLong(0xff);
        testUnsignedLong(0xff + 1);
        testUnsignedLong(0xff - 1);
        testUnsignedLong(811233478);
        testUnsignedLong(12345678);
        testUnsignedLong(2048 * 2000);
        testUnsignedLong(2048 * 20000);
        testUnsignedLong(2048 * 2048 * 2048);
        testUnsignedLong(2048 * 2048 * 2048 * 2048);
        testUnsignedLong(Integer.MAX_VALUE);
        testUnsignedLong(Long.MAX_VALUE);

        testUnsignedLong(-1);
        testUnsignedLong(-811233478);
        testUnsignedLong(Integer.MIN_VALUE);
        testUnsignedLong(Long.MIN_VALUE);
    }

    private void testUnsignedLong(long expected)
    {
        ByteBuffer buf = ByteBuffer.allocate(8);
        Wire.putUInt64(buf, expected);
        long actual = Wire.getUInt64(buf, 0);
        assertThat(actual, is(expected));
    }

    @Test
    public void testShortString()
    {
        testShortString("abcdef", 0);
        testShortString("ghijklm", 3);
    }

    @Test
    public void testShortStringOutOfByteRange()
    {
        testShortString(string(Byte.MAX_VALUE + 1, 'C'), 0);
    }

    @Test
    public void testShortStringMax()
    {
        testShortString(string(255, 'C'), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShortStringTooLong()
    {
        testShortString(string(256, 'B'), 0);
    }

    @Test
    public void testLongString()
    {
        testLongString("abcdef", 1);
        testLongString(string(256, 'D'), 0);
    }

    private void testShortString(String expected, int offset)
    {
        ByteBuffer buf = ByteBuffer.allocate(expected.length() + 1 + offset);
        buf.position(offset);
        Wire.putShortString(buf, expected);
        String actual = Wire.getShortString(buf, offset);
        assertThat(actual, is(expected));
    }

    private void testLongString(String expected, int offset)
    {
        ByteBuffer buf = ByteBuffer.allocate(expected.length() + 4 + offset);
        buf.position(offset);
        Wire.putLongString(buf, expected);
        String actual = Wire.getLongString(buf, offset);
        assertThat(actual, is(expected));
    }

    private String string(int length, char letter)
    {
        byte[] content = new byte[length];
        Arrays.fill(content, (byte) letter);
        return new String(content);
    }
}
