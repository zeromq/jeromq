package zmq;

import java.nio.ByteBuffer;

public interface IDecoder
{
    public void set_msg_sink (IMsgSink msg_sink);
    
    public ByteBuffer get_buffer ();
    
    public int process_buffer(ByteBuffer buf_, int size_);
    
    public boolean stalled ();
}
