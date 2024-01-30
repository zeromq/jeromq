package zmq.msg;

import zmq.Msg;

public class MsgAllocatorHeap implements MsgAllocator
{
    @Override
    public Msg allocate(int size)
    {
        return new Msg(size);
    }
}
