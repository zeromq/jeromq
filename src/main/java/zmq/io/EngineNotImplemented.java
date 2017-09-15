package zmq.io;

public class EngineNotImplemented implements IEngine
{
    public EngineNotImplemented()
    {
        throw new UnsupportedOperationException(getClass().getName() + " is not implemented");
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
