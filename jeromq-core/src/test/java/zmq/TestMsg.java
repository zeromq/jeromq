package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

import zmq.util.function.Function;

public class TestMsg
{
    private final Function<Integer, ByteBuffer> allocator;

    public TestMsg()
    {
        this(ByteBuffer::allocateDirect);
    }

    protected TestMsg(Function<Integer, ByteBuffer> allocator)
    {
        this.allocator = allocator;
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForNullByteBuffer()
    {
        new Msg((ByteBuffer) null);
    }

    @Test
    public void shouldWorkForFlippedBuffers()
    {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.putChar('a');
        buffer.putChar('b');
        buffer.putChar('c');
        buffer.flip();
        new Msg(buffer);
    }

    @Test
    public void testGetBytes()
    {
        final Msg msg = initMsg();

        final byte[] dst = new byte[3];
        msg.getBytes(0, dst, 0, 3);
        assertThat(dst, is(new byte[] { 0, 1, 2 }));
    }

    @Test
    public void testGetBytesIndex()
    {
        final Msg msg = initMsg();

        final byte[] dst = new byte[4];
        msg.getBytes(1, dst, 0, 4);
        assertThat(dst, is(new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    public void testGetBytesLength()
    {
        final Msg msg = initMsg();

        final byte[] dst = new byte[5];
        msg.getBytes(2, dst, 0, 2);
        assertThat(dst, is(new byte[] { 2, 3, 0, 0, 0 }));
    }

    @Test
    public void testGetBytesOffset()
    {
        final Msg msg = initMsg();

        final byte[] dst = new byte[6];
        msg.getBytes(3, dst, 1, 2);
        assertThat(dst, is(new byte[] { 0, 3, 4, 0, 0, 0 }));
    }

    @Test
    public void testPutString()
    {
        final Msg msg = initMsg();

        msg.putShortString("data");
        final byte[] dst = new byte[5];
        msg.getBytes(0, dst, 0, 5);
        assertThat(dst, is(new byte[] { 4, 'd', 'a', 't', 'a' }));
    }

    @Test
    public void testPutStringInBuilder()
    {
        final Msg.Builder msg = new Msg.Builder();

        msg.putShortString("data");
        final byte[] dst = new byte[5];
        msg.build().getBytes(0, dst, 0, 5);
        assertThat(dst, is(new byte[] { 4, 'd', 'a', 't', 'a' }));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutStringLongerThan255()
    {
        final Msg msg = initMsg();

        char[] charArray = new char[256];
        Arrays.fill(charArray, ' ');
        String str = new String(charArray);

        msg.putShortString(str);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutStringLongerThan255InBuilder()
    {
        final Msg.Builder msg = new Msg.Builder();

        char[] charArray = new char[256];
        Arrays.fill(charArray, ' ');
        String str = new String(charArray);

        msg.putShortString(str);
    }

    protected Msg initMsg()
    {
        return initDirectMsg(allocator);
    }

    Msg initDirectMsg(Function<Integer, ByteBuffer> allocator)
    {
        int size = 30;
        final ByteBuffer buffer = allocator.apply(size);
        for (int idx = 0; idx < size; ++idx) {
            buffer.put((byte) idx);
        }
        buffer.position(0);
        return new Msg(buffer);
    }

    // Check that data returned by Msg#getBytes(int, byte[], int, int) and Msg#get(int) are
    // consistent.
    @Test
    public void testGetBytesSameAsGet()
    {
        Msg msg1 = new Msg(new byte[] {42});
        Msg msg2 = new Msg(msg1);

        msg2.put(5);

        byte firstByte = msg2.get(0);

        byte[] data = new byte[1];
        msg2.getBytes(0, data, 0, 1);

        assertThat(data[0], is(firstByte));
    }

    // Check that Msg#data() is correct when the backing array has an offset.
    @Test
    public void testDataNonZeroOffset()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 1, 2).slice();
        Msg msg = new Msg(buffer);

        assertThat(msg.data(), is(new byte[]{11, 12}));
    }

    // Check that Msg#data() is correct when the end of the backing array is not used by the buffer.
    @Test
    public void testDataArrayExtendsFurther()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 2).slice();
        Msg msg = new Msg(buffer);

        assertThat(msg.data(), is(new byte[]{10, 11}));
    }

    // Check that data returned by Msg#getBytes(int, byte[], int, int) is correct when the backing
    // array has an offset.
    @Test
    public void testGetBytesNonZeroOffset()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 1, 2).slice();
        Msg msg = new Msg(buffer);

        byte[] gotData = new byte[2];
        msg.getBytes(0, gotData, 0, 2);

        assertThat(msg.data(), is(new byte[]{11, 12}));
    }

    // Check that data returned by Msg#getBytes(int, byte[], int, int) is correct when the end of
    // the backing array is not used by the buffer.
    @Test
    public void testGetBytesArrayExtendsFurther()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 2).slice();
        Msg msg = new Msg(buffer);

        byte[] gotData = new byte[2];
        msg.getBytes(0, gotData, 0, 2);

        assertThat(msg.data(), is(new byte[]{10, 11}));
    }

    // Check that Msg#data() doesn't make unnecessary copies.
    @Test
    public void testDataNoCopy()
    {
        byte[] data = new byte[]{10, 11, 12};

        Msg msg = new Msg(data);

        assertThat(msg.data(), sameInstance(data));
    }
}
