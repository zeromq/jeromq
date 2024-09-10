package zmq.io.net.pgm;

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

public class PgmNetworkProtocolProvider implements NetworkProtocolProvider<InetSocketAddress>
{
    @Override
    public boolean handleProtocol(NetProtocol protocol)
    {
        return protocol == NetProtocol.pgm;
    }

    @Override
    public Listener getListener(IOThread ioThread, SocketBase socket,
                                Options options)
    {
        return null;
    }

    @Override
    public IZAddress<InetSocketAddress> zresolve(String addr, boolean ipv6)
    {
        return null;
    }

    @Override
    public void startConnecting(Options options, IOThread ioThread,
                                SessionBase session, Address<InetSocketAddress> addr,
                                boolean delayedStart, Consumer<Own> launchChild,
                                BiConsumer<SessionBase, IEngine> sendAttach)
    {
        assert (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB || options.type == ZMQ.ZMQ_SUB
                        || options.type == ZMQ.ZMQ_XSUB);

                //  For EPGM transport with UDP encapsulation of PGM is used.
                boolean udpEncapsulation = withUdpEncapsulation();

                //  At this point we'll create message pipes to the session straight
                //  away. There's no point in delaying it as no concept of 'connect'
                //  exists with PGM anyway.
                if (options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_XPUB) {
                    //  PGM sender.
                    PgmSender pgmSender = new PgmSender(ioThread, options);
                    boolean rc = pgmSender.init(udpEncapsulation, addr);
                    assert (rc);
                    sendAttach.accept(session, pgmSender);
                }
                else {
                    //  PGM receiver.
                    PgmReceiver pgmReceiver = new PgmReceiver(ioThread, options);
                    boolean rc = pgmReceiver.init(udpEncapsulation, addr);
                    assert (rc);
                    sendAttach.accept(session, pgmReceiver);
                }
    }

    protected boolean withUdpEncapsulation()
    {
        return false;
    }

    @Override
    public boolean wantsIOThread()
    {
        return false;
    }
}
