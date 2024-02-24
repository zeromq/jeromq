package zmq.io.net.pgm;

import zmq.Options;
import zmq.io.EngineNotImplemented;
import zmq.io.IOThread;
import zmq.io.net.Address;

// TODO V4 implement pgm sender
public class PgmSender extends EngineNotImplemented
{
    public PgmSender(IOThread ioThread, Options options)
    {
        throw new UnsupportedOperationException();
    }

    public boolean init(boolean udpEncapsulation, Address addr)
    {
        return false;
    }
}
