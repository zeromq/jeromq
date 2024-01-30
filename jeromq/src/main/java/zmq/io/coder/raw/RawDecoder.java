package zmq.io.coder.raw;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.io.coder.IDecoder;
import zmq.util.ValueReference;

public class RawDecoder implements IDecoder
{
    //  The buffer for data to decode.
    private final ByteBuffer buffer;

    protected Msg inProgress;

    public RawDecoder(int bufsize)
    {
        buffer = ByteBuffer.allocateDirect(bufsize);
        inProgress = new Msg();
    }

    @Override
    public ByteBuffer getBuffer()
    {
        buffer.clear();
        return buffer;
    }

    @Override
    public Step.Result decode(ByteBuffer buffer, int size, ValueReference<Integer> processed)
    {
        processed.set(size);
        inProgress = new Msg(size);
        inProgress.put(buffer);

        return Step.Result.DECODED;
    }

    @Override
    public Msg msg()
    {
        return inProgress;
    }

    @Override
    public void destroy()
    {
    }
}
