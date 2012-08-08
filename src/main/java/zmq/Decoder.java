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
        one_byte_size_ready,
        eight_byte_size_ready,
        flags_ready,
        message_ready
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
    
    private boolean call_next() {
        switch(next) {
        case one_byte_size_ready:
            return one_byte_size_ready ();
        case eight_byte_size_ready:
            return eight_byte_size_ready ();
        case flags_ready:
            return flags_ready ();
        case message_ready:
            return message_ready ();
        default:
            throw new IllegalStateException(next.toString());
        }
    }

    private boolean message_ready() {
        throw new UnsupportedOperationException();
    }

    private boolean flags_ready() {
        throw new UnsupportedOperationException();

    }

    private boolean eight_byte_size_ready() {
        throw new UnsupportedOperationException();

    }

    private boolean one_byte_size_ready() {
        throw new UnsupportedOperationException();

    }

    public int process_buffer(byte[] data_, int start_, int size_) {
        //  Check if we had an error in previous attempt.
        if (next == null)
            return -1;

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (start_ == read_pos) {
            read_pos += size_;
            to_read -= size_;

            while (to_read == 0) {
                if (!call_next()) {
                    if (next == null)
                        return -1;
                    return size_;
                }
            }
            return size_;
        }

        int pos = 0;
        while (true) {

            //  Try to get more space in the message to fill in.
            //  If none is available, return.
            while (to_read == 0) {
                if (!call_next ()) {
                    if (next == null)
                        return -1;
                    return pos;
                }
            }

            //  If there are no more data in the buffer, return.
            if (pos == size_)
                return pos;
            //  Copy the data from buffer to the message.
            int to_copy = Math.min (to_read, size_ - pos);
            Utils.memcpy (data_, read_pos, data_ , pos, to_copy);
            read_pos += to_copy;
            pos += to_copy;
            to_read -= to_copy;
        }
    }
    
    public boolean stalled ()
    {
        return next == Step.message_ready;
    }


    public void set_session(SessionBase session_) {
        session = session_;
    }


}
