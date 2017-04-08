package zmq.io.coder.raw;

import zmq.io.coder.Encoder;
import zmq.util.Errno;

//  Encoder for 0MQ framing protocol. Converts messages into data batches.

public class RawEncoder extends Encoder
{
    public RawEncoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
        //  Write 0 bytes to the batch and go to messageReady state.
        initStep(messageReady, true);
    }

    @Override
    protected void sizeReady()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void messageReady()
    {
        nextStep(inProgress.buf(), inProgress.size(), messageReady, true);
    }
}
