package zmq.io.mechanism.gssapi;

import zmq.Msg;
import zmq.Options;
import zmq.io.mechanism.Mechanism;

// TODO V4 implement GSSAPI
public class GssapiClientMechanism extends Mechanism
{
    public GssapiClientMechanism(Options options)
    {
        super(null, null, options);
        throw new UnsupportedOperationException("GSSAPI mechanism is not yet implemented");
    }

    @Override
    public Status status()
    {
        return null;
    }

    @Override
    public int zapMsgAvailable()
    {
        return 0;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        return 0;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        return 0;
    }
}
