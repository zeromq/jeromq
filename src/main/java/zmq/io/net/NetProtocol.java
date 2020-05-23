package zmq.io.net;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.ipc.IpcAddress;
import zmq.io.net.ipc.IpcListener;
import zmq.io.net.tcp.TcpAddress;
import zmq.io.net.tcp.TcpListener;
import zmq.io.net.tipc.TipcListener;
import zmq.socket.Sockets;

public enum NetProtocol
{
    inproc(true, false, false),
    ipc(true, false, false)
    {
        @Override
        public Listener getListener(IOThread ioThread, SocketBase socket,
                                    Options options)
        {
            return new IpcListener(ioThread, socket, options);
        }

        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }

        @Override
        public IZAddress zresolve(String addr, boolean ipv6)
        {
            return new IpcAddress(addr);
        }

    },
    tcp(true, false, false)
    {
        @Override
        public Listener getListener(IOThread ioThread, SocketBase socket,
                                    Options options)
        {
            return new TcpListener(ioThread, socket, options);
        }

        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }

        @Override
        public IZAddress zresolve(String addr, boolean ipv6)
        {
            return  new TcpAddress(addr, ipv6);
        }

    },
    //  PGM does not support subscription forwarding; ask for all data to be
    //  sent to this pipe. (same for NORM, currently?)
    pgm(false, true, true, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    epgm(false, true, true, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    tipc(false, false, false)
    {
        @Override
        public Listener getListener(IOThread ioThread, SocketBase socket,
                                    Options options)
        {
            return new TipcListener(ioThread, socket, options);
        }

        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }

    },
    norm(false, true, true);

    public final boolean  valid;
    public final boolean  subscribe2all;
    public final boolean  isMulticast;
    private Set<Integer> compatibles;

    NetProtocol(boolean implemented, boolean subscribe2all, boolean isMulticast, Sockets... compatibles)
    {
        valid = implemented;
        this.compatibles = new HashSet<>(compatibles.length);
        for (Sockets s : compatibles) {
            this.compatibles.add(s.ordinal());
        }
        this.subscribe2all = subscribe2all;
        this.isMulticast = isMulticast;
    }

    /**
     * @param protocol name
     * @throws IllegalArgumentException if the protocol name can be matched to an actual supported protocol
     * @return
     */
    public static NetProtocol getProtocol(String protocol)
    {
        try {
            return valueOf(protocol.toLowerCase(Locale.ENGLISH));
        }
        catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown protocol: \"" + protocol + "\"");
        }
    }

    public final boolean compatible(int type)
    {
        return compatibles.isEmpty() || compatibles.contains(type);
    }

    public Listener getListener(IOThread ioThread, SocketBase socket, Options options)
    {
        return null;
    }

    public void resolve(Address paddr, boolean ipv6)
    {
        // TODO V4 init address for pgm & epgm
    }

    public IZAddress zresolve(String addr, boolean ipv6)
    {
        return null;
    }
}
