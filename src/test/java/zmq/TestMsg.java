package zmq;

import org.junit.Test;
import java.nio.ByteBuffer;

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
