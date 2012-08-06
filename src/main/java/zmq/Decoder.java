package zmq;

import java.nio.ByteBuffer;

public class Decoder {
    
    //  Where to store the read data.
    private ByteBuffer read_buf;
    private int read_pos;

    //  How much data to read before taking next step.
    private int to_read;

    //  The duffer for data to decode.
    private int bufsize;
    private ByteBuffer buf;
    
    private enum Step {
        one_byte_size_ready;
    }
    private Step next;
    
    private SessionBase session;
    final private ByteBuffer tmpbuf;
    private Msg in_progress;

    private long maxmsgsize;

    public Decoder (int bufsize_, long maxmsgsize_)
    {
        next = null;
        read_pos = 0; 
        to_read = 0;
        bufsize = bufsize_;
        session = null;
        maxmsgsize = maxmsgsize_;
        buf = ByteBuffer.allocate(bufsize_);
        tmpbuf = ByteBuffer.allocate(8);

        int rc = in_progress.init ();
        Errno.errno_assert (rc == 0);
    
        //  At the beginning, read one byte and go to one_byte_size_ready state.
        next_step (tmpbuf, 0, 1, Step.one_byte_size_ready);
    }
    
    private void next_step (ByteBuffer buf_, int read_pos_, int to_read_,
            Step next_)
    {
        read_buf = buf_;
        read_pos = read_pos_;
        to_read = to_read_;
        next = next_;
    }

}
