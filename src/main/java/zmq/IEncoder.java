package zmq;

import java.nio.ByteBuffer;

public interface IEncoder
{
    //  Set message producer.
    public void set_msg_source (IMsgSource msg_source_);

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  is nullL) encoder will provide buffer of its own.
    public Transfer get_data (ByteBuffer data_) ;

    public boolean has_data ();
}
