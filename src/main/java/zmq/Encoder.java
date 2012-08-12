package zmq;

import java.nio.ByteBuffer;

public class Encoder extends EncoderBase {

    private enum Step {
        size_ready,
        message_ready
    }
    

    private Msg in_progress;
    final private ByteBuffer tmpbuf;
    
    public Encoder(int bufsize_) {
        super(bufsize_);
        tmpbuf = ByteBuffer.allocate(10);
        
        //  Write 0 bytes to the batch and go to message_ready state.
        next_step (null, 0, Step.message_ready, true);
    }

    
    @Override
    protected boolean next() {
        switch((Step)state()) {
        case size_ready:
            return size_ready ();
        case message_ready:
            return message_ready ();
        default:
            throw new IllegalStateException(state().toString());
        }
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
        in_progress = session_read ();
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
        tmpbuf.clear();
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
        tmpbuf.flip();
        
        return true;
    }


}
