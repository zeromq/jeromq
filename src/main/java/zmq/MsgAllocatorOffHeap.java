package zmq;

import java.nio.ByteBuffer;

public class MsgAllocatorOffHeap implements MsgAllocator
{
   @Override
   public Msg allocate(int size)
   {
      return new Msg(ByteBuffer.allocateDirect(size));
   }
}
