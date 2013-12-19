package zmq;

import java.nio.ByteBuffer;

public class V1Decoder extends DecoderBase
{
    
    private static final int one_byte_size_ready = 0;
    private static final int eight_byte_size_ready = 1;
    private static final int flags_ready = 2;
    private static final int message_ready = 3;
    
    private final byte[] tmpbuf;
    private Msg in_progress;
    private IMsgSink msg_sink;
    private final long maxmsgsize;
    private int msg_flags;
    
    public V1Decoder (int bufsize_, long maxmsgsize_, IMsgSink session)
    {
        super (bufsize_);

        maxmsgsize = maxmsgsize_;
        msg_sink = session;
        
        tmpbuf = new byte[8];
        
        //  At the beginning, read one byte and go to one_byte_size_ready state.
        next_step (tmpbuf, 1, flags_ready);
    }

    //  Set the receiver of decoded messages.
    @Override
    public void set_msg_sink (IMsgSink msg_sink_) 
    {
        msg_sink = msg_sink_;
    }

    @Override
    protected boolean next() {
        switch(state()) {
        case one_byte_size_ready:
            return one_byte_size_ready ();
        case eight_byte_size_ready:
            return eight_byte_size_ready ();
        case flags_ready:
            return flags_ready ();
        case message_ready:
            return message_ready ();
        default:
            return false;
        }
    }



    private boolean one_byte_size_ready () {
        
        int size = tmpbuf [0];
        if (size < 0)
            size = (0xff) & size;

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0)
            if (size > maxmsgsize) {
                decoding_error ();
                return false;
            }

        //  in_progress is initialised at this point so in theory we should
        //  close it before calling zmq_msg_init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised...
        in_progress = new Msg (size);

        in_progress.setFlags (msg_flags);
        next_step (in_progress.data (), in_progress.size (),
            message_ready);

        return true;
    }
    
    private boolean eight_byte_size_ready() {
        
        //  The payload size is encoded as 64-bit unsigned integer.
        //  The most significant byte comes first.
        final long msg_size = ByteBuffer.wrap(tmpbuf).getLong();

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0)
            if (msg_size > maxmsgsize) {
                decoding_error ();
                return false;
            }

        //  Message size must fit within range of size_t data type.
        if (msg_size > Integer.MAX_VALUE) {
            decoding_error ();
            return false;
        }
        
        //  in_progress is initialised at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised.
        in_progress = new Msg ((int) msg_size);

        in_progress.setFlags (msg_flags);
        next_step (in_progress.data (), in_progress.size (),
                message_ready);

        return true;
    }
    
    private boolean flags_ready() {

        //  Store the flags from the wire into the message structure.
        msg_flags = 0;
        int first = tmpbuf[0];
        if ((first & V1Protocol.MORE_FLAG) > 0)
            msg_flags |= Msg.MORE;
        
        //  The payload length is either one or eight bytes,
        //  depending on whether the 'large' bit is set.
        if ((first & V1Protocol.LARGE_FLAG) > 0)
            next_step (tmpbuf, 8, eight_byte_size_ready);
        else
            next_step (tmpbuf, 1, one_byte_size_ready);
        
        return true;

    }
    
    private boolean message_ready() {
        //  Message is completely read. Push it further and start reading
        //  new message. (in_progress is a 0-byte message after this point.)
        
        if (msg_sink == null)
            return false;
        
        int rc = msg_sink.push_msg(in_progress);
        if (rc != 0) {
            if (rc != ZError.EAGAIN)
                decoding_error ();
            
            return false;
        }
        
        next_step (tmpbuf, 1, flags_ready);
        
        return true;
    }

}
