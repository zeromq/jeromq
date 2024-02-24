package zmq.msg;

import zmq.Config;
import zmq.Msg;

public class MsgAllocatorThreshold implements MsgAllocator
{
    private static final MsgAllocator direct = new MsgAllocatorDirect();
    private static final MsgAllocator heap   = new MsgAllocatorHeap();

    public final int threshold;

    public MsgAllocatorThreshold()
    {
        this(Config.MSG_ALLOCATION_HEAP_THRESHOLD.getValue());
    }

    public MsgAllocatorThreshold(int threshold)
    {
        this.threshold = threshold;
    }

    @Override
    public Msg allocate(int size)
    {
        if (threshold > 0 && size > threshold) {
            return direct.allocate(size);
        }
        else {
            return heap.allocate(size);
        }
    }
}
