package zmq;

import java.nio.ByteBuffer;

public class Encoder {

    private enum Step {
        size_ready,
        message_ready
    }
    
    //  Where to get the data to write from.
    private ByteBuffer write_buf;
    private int write_pos;

    //  How much data to write before next step should be executed.
    private int to_write;

    //  Next step. If set to NULL, it means that associated data stream
    //  is dead.
    private Step next;

    //  If true, first byte of the message is being written.
    private boolean beginning;

    //  The buffer for encoded data.
    private int bufsize;
    private ByteBuffer buf;
    
    private SessionBase session;
    private Msg in_progress;
    final private ByteBuffer tmpbuf;
    
    public Encoder(int bufsize_) {
        bufsize = bufsize_; 
        buf = ByteBuffer.allocate(bufsize_);
        tmpbuf = ByteBuffer.allocate(10);
        
        int rc = in_progress.init ();
        Errno.errno_assert (rc == 0);

        //  Write 0 bytes to the batch and go to message_ready state.
        next_step (null, 0, 0, Step.message_ready, true);
    }
    
    private void next_step (ByteBuffer buf_, int write_pos_, int to_write_,
            Step next_, boolean beginning_)
    {
        write_buf = buf_;
        write_pos = write_pos_;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }

    public void set_session(SessionBase session_) {
        session = session_;
    }

}
