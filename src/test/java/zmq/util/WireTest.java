package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

public class WireTest
{
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
}
