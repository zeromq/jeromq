package zmq.msg;

import zmq.Msg;

public interface MsgAllocator
{
    Msg allocate(int size);
}
