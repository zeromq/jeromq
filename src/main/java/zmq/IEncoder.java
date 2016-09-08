package zmq;

import java.nio.ByteBuffer;

public interface IEncoder
{
    //  Set message producer.
    public void setMsgSource(IMsgSource msgSource);

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  is null) encoder will provide buffer of its own.
    public Transfer getData(ByteBuffer buffer);

    public boolean hasData();
}
