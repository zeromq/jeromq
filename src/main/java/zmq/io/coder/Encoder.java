package zmq.io.coder;

import zmq.util.Errno;

public abstract class Encoder extends EncoderBase
{
    protected final Runnable sizeReady = this::sizeReady;

    protected final Runnable messageReady = this::messageReady;

    protected Encoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
    }

    protected abstract void sizeReady();

    protected abstract void messageReady();
}
