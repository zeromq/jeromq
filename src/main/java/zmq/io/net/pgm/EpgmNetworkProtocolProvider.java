package zmq.io.net.pgm;

import zmq.io.net.NetProtocol;

public class EpgmNetworkProtocolProvider extends PgmNetworkProtocolProvider
{
    @Override
    public boolean handleProtocol(NetProtocol protocol)
    {
        return protocol == NetProtocol.epgm;
    }

    @Override
    protected boolean withUdpEncapsulation()
    {
        return true;
    }
}
