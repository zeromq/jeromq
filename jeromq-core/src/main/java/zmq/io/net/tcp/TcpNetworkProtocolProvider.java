package zmq.io.net.tcp;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.Listener;
import zmq.io.net.NetProtocol;
import zmq.io.net.NetworkProtocolProvider;

public class TcpNetworkProtocolProvider implements NetworkProtocolProvider<InetSocketAddress>
{
    @Override
    public boolean handleProtocol(NetProtocol protocol)
    {
        return protocol == NetProtocol.tcp;
    }

    @Override
    public Listener getListener(IOThread ioThread, SocketBase socket,
                                Options options)
    {
        return new TcpListener(ioThread, socket, options);
    }

    @Override
    public IZAddress<InetSocketAddress> zresolve(String addr, boolean ipv6)
    {
        return new TcpAddress(addr, ipv6);
    }

    @Override
    public void startConnecting(Options options, IOThread ioThread,
                                SessionBase session, Address<InetSocketAddress> addr,
                                boolean delayedStart, Consumer<Own> launchChild,
                                BiConsumer<SessionBase, IEngine> sendAttach)
    {
        if (options.socksProxyAddress != null) {
            Address proxyAddress = new Address(NetProtocol.tcp, options.socksProxyAddress);
            SocksConnecter connecter = new SocksConnecter(ioThread, session, options, addr, proxyAddress, delayedStart);
            launchChild.accept(connecter);
        }
        else {
            TcpConnecter connecter = new TcpConnecter(ioThread, session, options, addr, delayedStart);
            launchChild.accept(connecter);
        }
    }

    @Override
    public boolean isValid()
    {
        return true;
    }

    @Override
    public boolean handleAdress(SocketAddress socketAddress)
    {
        return socketAddress instanceof InetSocketAddress;
    }

    @Override
    public String formatSocketAddress(InetSocketAddress socketAddress)
    {
        return socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort();
    }

    @Override
    public boolean wantsIOThread()
    {
        return true;
    }
}
