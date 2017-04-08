package zmq.io.net.pgm;

import zmq.Options;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;

// TODO V4 implement pgm sender
public class PgmSender implements IEngine
{
    public PgmSender(IOThread ioThread, Options options)
    {
        throw new UnsupportedOperationException();
    }

    public boolean init(boolean udpEncapsulation, Address addr)
    {
        return false;
    }

    @Override
    public void plug(IOThread ioThread, SessionBase session)
    {
    }

    @Override
    public void terminate()
    {
    }

    @Override
    public void restartInput()
    {
    }

    @Override
    public void restartOutput()
    {
    }

    @Override
    public void zapMsgAvailable()
    {
    }
}
