package zmq.io.coder;

import zmq.util.Errno;

public abstract class Encoder extends EncoderBase
{
    protected final Runnable sizeReady = new Runnable()
    {
        @Override
        public void run()
        {
            sizeReady();
        }

    };

    protected final Runnable messageReady = new Runnable()
    {
        @Override
        public void run()
        {
            messageReady();
        }

    };

    protected Encoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
    }

    protected abstract void sizeReady();

    protected abstract void messageReady();
}
