package zmq.msg;

import java.nio.ByteBuffer;

import zmq.Msg;

public class MsgAllocatorDirect implements MsgAllocator
{
    @Override
    public Msg allocate(int size)
    {
        return new Msg(ByteBuffer.allocateDirect(size));
    }
}
