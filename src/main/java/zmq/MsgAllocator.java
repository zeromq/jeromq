package zmq;

public interface MsgAllocator
{
   /**
    * Allocate a Msg based on users needs (e.g., using DirectByteBuffer with additional space in
    * front for header information).
    *
    * @param size The size of the Msg.
    * @return Msg The ByteBuffer Msg to meet space requirements
    */
   Msg allocate(int size);
}
