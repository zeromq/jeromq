package zmq.io.net.norm;

import zmq.Options;
import zmq.io.EngineNotImplemented;
import zmq.io.IOThread;
import zmq.io.net.Address;

// TODO V4 implement NORM engine
public class NormEngine extends EngineNotImplemented
{
    public NormEngine(IOThread ioThread, Options options)
    {
        throw new UnsupportedOperationException();
    }

    public boolean init(Address addr, boolean b, boolean c)
    {
        return false;
    }
}
