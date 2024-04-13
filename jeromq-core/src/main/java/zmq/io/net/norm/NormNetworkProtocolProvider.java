package zmq.io.net.norm;

import java.net.InetSocketAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.Listener;
import zmq.io.net.NetProtocol;
import zmq.io.net.NetworkProtocolProvider;

public class NormNetworkProtocolProvider implements NetworkProtocolProvider<InetSocketAddress>
{
    @Override
    public boolean handleProtocol(NetProtocol protocol)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Listener getListener(IOThread ioThread, SocketBase socket,
                                Options options)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IZAddress<InetSocketAddress> zresolve(String addr, boolean ipv6)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void startConnecting(Options options, IOThread ioThread,
                                SessionBase session, Address<InetSocketAddress> addr,
                                boolean delayedStart,
                                Consumer<Own> launchChild,
                                BiConsumer<SessionBase, IEngine> sendAttach)
    {
        //  At this point we'll create message pipes to the session straight
        //  away. There's no point in delaying it as no concept of 'connect'
        //  exists with NORM anyway.
        if (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB) {
            //  NORM sender.
            NormEngine normSender = new NormEngine(ioThread, options);
            boolean rc = normSender.init(addr, true, false);
            assert (rc);
            sendAttach.accept(session, normSender);
        }
        else {
            //  NORM receiver.
            NormEngine normReceiver = new NormEngine(ioThread, options);
            boolean rc = normReceiver.init(addr, false, true);
            assert (rc);
            sendAttach.accept(session, normReceiver);
        }
    }

    @Override
    public boolean wantsIOThread()
    {
        return false;
    }
}
