package zmq.io.coder;

import zmq.util.Errno;

public abstract class Encoder extends EncoderBase
{
    protected final Runnable sizeReady = () -> sizeReady();

    protected final Runnable messageReady = () -> messageReady();

    protected Encoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
    }

    protected abstract void sizeReady();

    protected abstract void messageReady();
}
