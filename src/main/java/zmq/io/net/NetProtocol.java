package zmq.io.net;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.inproc.InprocNetworkProtocolProvider;
import zmq.io.net.ipc.IpcNetworkProtocolProvider;
import zmq.io.net.norm.NormNetworkProtocolProvider;
import zmq.io.net.pgm.EpgmNetworkProtocolProvider;
import zmq.io.net.pgm.PgmNetworkProtocolProvider;
import zmq.io.net.tcp.TcpNetworkProtocolProvider;
import zmq.io.net.tipc.TipcNetworkProtocolProvider;
import zmq.socket.Sockets;

public enum NetProtocol
{
    inproc(false, false),
    tcp(false, false)
    {
        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    udp(true, true)
    {
        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    //  PGM does not support subscription forwarding; ask for all data to be
    //  sent to this pipe. (same for NORM, currently?)
    pgm(true, true, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    epgm(true, true, Sockets.PUB, Sockets.SUB, Sockets.XPUB, Sockets.XPUB),
    norm(true, true),
    ws(true, true),
    wss(true, true),
    ipc(false, false)
    {
        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    tipc(false, false)
    {
        @Override
        public void resolve(Address paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    vmci(true, true),
    ;

    private static final Map<NetProtocol,NetworkProtocolProvider > providers;
    static {
        providers = new HashMap<>(NetProtocol.values().length);
        providers.put(NetProtocol.tcp, new TcpNetworkProtocolProvider());
        providers.put(NetProtocol.ipc, new IpcNetworkProtocolProvider());
        providers.put(NetProtocol.tipc, new TipcNetworkProtocolProvider());
        providers.put(NetProtocol.norm, new NormNetworkProtocolProvider());
        providers.put(NetProtocol.inproc, new InprocNetworkProtocolProvider());
        providers.put(NetProtocol.pgm, new PgmNetworkProtocolProvider());
        providers.put(NetProtocol.epgm, new EpgmNetworkProtocolProvider());
    }

    public final boolean  subscribe2all;
    public final boolean  isMulticast;
    private Set<Integer> compatibles;

    private NetProtocol(boolean subscribe2all, boolean isMulticast, Sockets... compatibles)
    {
        this.compatibles = new HashSet<>(compatibles.length);
        for (Sockets s : compatibles) {
            this.compatibles.add(s.ordinal());
        }
        this.subscribe2all = subscribe2all;
        this.isMulticast = isMulticast;
    }

    public boolean isValid() {
        return providers.containsKey(this) && providers.get(this).isValid();
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
        return providers.get(this).getListener(ioThread, socket, options);
    }

    public void resolve(Address paddr, boolean ipv6)
    {
        // TODO V4 init address for pgm & epgm
    }

    public IZAddress zresolve(String addr, boolean ipv6)
    {
        return providers.get(this).zresolve(addr, ipv6);
    }

    public void startConnecting(Options options, IOThread ioThread, SessionBase session, Address addr,
                                         boolean delayedStart, Consumer<Own> launchChild, BiConsumer<SessionBase, IEngine> sendAttach) {
        providers.get(this).startConnecting(options, ioThread, session, addr, delayedStart, launchChild, sendAttach);
    }
}
