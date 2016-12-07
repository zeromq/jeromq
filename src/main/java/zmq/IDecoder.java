package zmq;

import java.nio.ByteBuffer;

public interface IDecoder
{
    public void setMsgSink(IMsgSink msgSink);

    public ByteBuffer getBuffer();

    public int processBuffer(ByteBuffer buffer, int size);

    public boolean stalled();
}
