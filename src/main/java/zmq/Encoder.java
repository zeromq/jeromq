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
        
        //  Write 0 bytes to the batch and go to message_ready state.
        next_step (null, 0, Step.message_ready, true);
    }
    
    private void next_step (ByteBuffer buf_, int to_write_,
            Step next_, boolean beginning_)
    {
        write_buf = buf_;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }
    
    private boolean call_next() {
        switch(next) {
        case size_ready:
            return size_ready ();
        case message_ready:
            return message_ready ();
        default:
            throw new IllegalStateException(next.toString());
        }
    }


    public void set_session(SessionBase session_) {
        session = session_;
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  points to NULL) decoder object will provide buffer of its own.
    //  If offset is not NULL, it is filled by offset of the first message
    //  in the batch.If there's no beginning of a message in the batch,
    //  offset is set to -1.
    
    public ByteBuffer get_data(ByteBuffer data_, int[] offset_) {
        //unsigned char *buffer = !*data_ ? buf : *data_;
        //size_t buffersize = !*data_ ? bufsize : *size_;

        ByteBuffer buffer = (data_ == null)? buf : data_;
        int buffersize = buffer.remaining();
        if (offset_ != null)
            offset_[0] = -1;

        int pos = 0;
        while (buffer.hasRemaining()) {

            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (to_write == 0) {
                //  If we are to encode the beginning of a new message,
                //  adjust the message offset.
                if (beginning)
                    if (offset_ != null && offset_[0] == -1)
                        offset_[0] = buffer.position();

                if (!call_next())
                    break;
            }

            //  If there are no data in the buffer yet and we are able to
            //  fill whole buffer in a single go, let's use zero-copy.
            //  There's no disadvantage to it as we cannot stuck multiple
            //  messages into the buffer anyway. Note that subsequent
            //  write(s) are non-blocking, thus each single write writes
            //  at most SO_SNDBUF bytes at once not depending on how large
            //  is the chunk returned from here.
            //  As a consequence, large messages being sent won't block
            //  other engines running in the same I/O thread for excessive
            //  amounts of time.
            if (buffer.position() == 0 && data_ == null && to_write >= buffersize) {
                ByteBuffer t = write_buf;
                write_buf = null;
                to_write = 0;
                return t ;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int to_copy = Math.min (to_write, buffer.remaining());
            pos = write_buf.position();
            write_buf.get(buffer.array(), buffer.arrayOffset() + pos, to_copy);
            buffer.position(pos + to_copy);
            write_pos += to_copy;
            to_write -= to_copy;
        }

        return buffer;

    }
    
    private boolean size_ready ()
    {
        //  Write message body into the buffer.
        next_step (in_progress.data (), in_progress.size (),
            Step.message_ready, !in_progress.has_more());
        return true;
    }

    
    private boolean message_ready ()
    {
        //  Destroy content of the old message.
        //in_progress.close ();

        //  Read new message. If there is none, return false.
        //  Note that new state is set only if write is successful. That way
        //  unsuccessful write will cause retry on the next state machine
        //  invocation.
        if (session == null) {
            return false;
        }
        in_progress = session.read ();
        if (in_progress == null) {
            return false;
        }

        //  Get the message size.
        int size = in_progress.size ();

        //  Account for the 'flags' byte.
        size++;

        //  For messages less than 255 bytes long, write one byte of message size.
        //  For longer messages write 0xff escape character followed by 8-byte
        //  message size. In both cases 'flags' field follows.
        if (size < 255) {
            tmpbuf.put((byte)size);
            tmpbuf.put((byte) (in_progress.flags () & Msg.more));
            next_step (tmpbuf, 2,Step.size_ready, false);
        }
        else {
            tmpbuf.put((byte)0xff);
            tmpbuf.putLong (size);
            tmpbuf.put((byte) (in_progress.flags () & Msg.more));
            next_step (tmpbuf, 10, Step.size_ready, false);
        }
        
        return true;
    }


}
