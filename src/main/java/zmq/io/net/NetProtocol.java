package zmq.io.net;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address.IZAddress;
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
    vmci(true, true);

    private static final Map<NetProtocol, NetworkProtocolProvider> providers = new ConcurrentHashMap<>();

    /**
     * @param protocol name
     * @throws IllegalArgumentException if the protocol name can be matched to an actual supported protocol
     * @return {@link NetProtocol} resolved by name
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

    /**
     * <p>Load a {@link NetworkProtocolProvider} and ensure that if a class loader is given, it will be used. If multiple
     * instance are returned, the first one will be used.</p>
     *
     * <p>The purpose of this method is to be able to handle how a {@link NetworkProtocolProvider} service is resolver, by
     * tweaking CLASSPATH and class loader.</p>
     * @param proto The protocol to search
     * @param cl The class loader used to resolve the {@link NetworkProtocolProvider} or null if not required.
     */
    public static void loadWithClassLoader(NetProtocol proto, ClassLoader cl)
    {
        Optional.ofNullable(resolveProtocol(proto, cl))
                .ifPresent(npp -> providers.put(proto, npp));
    }

    private static NetworkProtocolProvider resolveProtocol(NetProtocol proto, ClassLoader cl)
    {
        ServiceLoader<NetworkProtocolProvider> serviceLoader = ServiceLoader.load(NetworkProtocolProvider.class, cl);
        return serviceLoader.stream()
                            .map(ServiceLoader.Provider::get)
                            .filter(npp -> npp.isValid() && npp.handleProtocol(proto) && (cl == null || npp.getClass().getClassLoader() == cl))
                            .findFirst()
                            .orElse(null);
    }

    private static NetworkProtocolProvider resolveProtocol(NetProtocol proto)
    {
        return resolveProtocol(proto, null);
    }

    /**
     * Install the requested {@link NetworkProtocolProvider}, overwriting the previously installed. Checks are done to
     * ensure that the provided protocol is valid
     * @param protocol The {@link NetProtocol}
     * @param provider The {@link NetworkProtocolProvider} to be installed.
     * @throws IllegalArgumentException if the provider is not usable for this protocol
     */
    public static void install(NetProtocol protocol, NetworkProtocolProvider provider)
    {
        if (provider.isValid() && provider.handleProtocol(protocol)) {
            providers.put(protocol, provider);
        }
        else {
            throw new IllegalArgumentException("The given provider can't handle " + protocol);
        }
    }

    public static NetProtocol findByAddress(SocketAddress socketAddress)
    {
        for (Map.Entry<NetProtocol, NetworkProtocolProvider> e : providers.entrySet()) {
            if (e.getValue().handleAdress(socketAddress)) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("Unhandled address protocol " + socketAddress);
    }

    public final boolean  subscribe2all;
    public final boolean  isMulticast;
    private final Set<Integer> compatibles;

    NetProtocol(boolean subscribe2all, boolean isMulticast, Sockets... compatibles)
    {
        this.compatibles = Arrays.stream(compatibles).map(Sockets::ordinal).collect(Collectors.toSet());
        this.subscribe2all = subscribe2all;
        this.isMulticast = isMulticast;
    }

    public boolean isValid()
    {
        return Optional.ofNullable(providers.computeIfAbsent(this, NetProtocol::resolveProtocol))
                       .map(NetworkProtocolProvider::isValid)
                       .orElse(false);
    }

    public final boolean compatible(int type)
    {
        return compatibles.isEmpty() || compatibles.contains(type);
    }

    public Listener getListener(IOThread ioThread, SocketBase socket, Options options)
    {
        return resolve().getListener(ioThread, socket, options);
    }

    public void resolve(Address paddr, boolean ipv6)
    {
        // TODO V4 init address for pgm & epgm
    }

    public IZAddress zresolve(String addr, boolean ipv6)
    {
        return resolve().zresolve(addr, ipv6);
    }

    public void startConnecting(Options options, IOThread ioThread, SessionBase session, Address addr,
                                         boolean delayedStart, Consumer<Own> launchChild, BiConsumer<SessionBase, IEngine> sendAttach)
    {
        resolve().startConnecting(options, ioThread, session, addr, delayedStart, launchChild, sendAttach);
    }

    private NetworkProtocolProvider resolve()
    {
        NetworkProtocolProvider protocolProvider = providers.computeIfAbsent(this, NetProtocol::resolveProtocol);
        if (protocolProvider == null || ! protocolProvider.isValid()) {
            throw new IllegalArgumentException("Unsupported network protocol " + this);
        }
        return protocolProvider;
    }

    String formatSocketAddress(SocketAddress socketAddress)
    {
        return resolve().formatSocketAddress(socketAddress);
    }
}
