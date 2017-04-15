package zmq;

import java.nio.ByteBuffer;

import org.junit.Test;

public class TestMsg
{
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
}
