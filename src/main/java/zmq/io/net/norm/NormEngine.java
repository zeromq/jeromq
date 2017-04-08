package zmq.io.net.norm;

import zmq.Options;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;

// TODO V4 implement NORM engine
public class NormEngine implements IEngine
{
    public NormEngine(IOThread ioThread, Options options)
    {
        throw new UnsupportedOperationException();
    }

    public boolean init(Address addr, boolean b, boolean c)
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
