package zmq;

public class IpcConnecter extends TcpConnecter
{
    public IpcConnecter(IOThread ioThread,
            SessionBase session, final Options options,
            final Address addr, boolean wait)
    {
        super(ioThread, session, options, addr, wait);
    }
}
