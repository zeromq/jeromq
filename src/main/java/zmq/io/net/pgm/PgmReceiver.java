package zmq.io.net.pgm;

import zmq.Options;
import zmq.io.EngineNotImplemented;
import zmq.io.IOThread;
import zmq.io.net.Address;

//TODO V4 implement pgm receiver
public class PgmReceiver extends EngineNotImplemented
{
    public PgmReceiver(IOThread ioThread, Options options)
    {
        throw new UnsupportedOperationException();
    }

    public boolean init(boolean udpEncapsulation, Address addr)
    {
        return false;
    }
}
