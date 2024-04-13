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
        public <S extends SocketAddress> void resolve(Address<S> paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    udp(true, true)
    {
        @Override
        public <S extends SocketAddress> void resolve(Address<S> paddr, boolean ipv6)
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
        public <S extends SocketAddress> void resolve(Address<S> paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    tipc(false, false)
    {
        @Override
        public <S extends SocketAddress> void resolve(Address<S> paddr, boolean ipv6)
        {
            paddr.resolve(ipv6);
        }
    },
    vmci(true, true);

    private static final Map<NetProtocol, NetworkProtocolProvider<? extends SocketAddress>> providers = new ConcurrentHashMap<>();

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
     * Preload some {@link NetworkProtocolProvider} provided by the specified class loader. Useful when you have a plugin
     * mechanism handled by a custom class loader. Previously installed providers are replaced, so it should be used from
     * the more generic to the more specific in case of complex hierarchy.
     * @param cl The class loader used to resolve the {@link NetworkProtocolProvider}.
     */
    @SuppressWarnings("rawtypes, unchecked")
    public static void preloadWithClassLoader(ClassLoader cl)
    {
        ServiceLoader<NetworkProtocolProvider> serviceLoader = ServiceLoader.load(NetworkProtocolProvider.class, cl);
        serviceLoader.stream()
                     .map(ServiceLoader.Provider::get)
                     .filter(npp -> npp.getClass().getClassLoader() == cl)
                     .forEach(npp -> {
                        for (NetProtocol np : NetProtocol.values()) {
                            if (npp.handleProtocol(np)) {
                                providers.put(np, npp);
                            }
                        }
                     });
    }

    @SuppressWarnings("rawtypes")
    private static NetworkProtocolProvider resolveProtocol(NetProtocol proto)
    {
        ServiceLoader<NetworkProtocolProvider> serviceLoader = ServiceLoader.load(NetworkProtocolProvider.class);
        return serviceLoader.stream()
                            .map(ServiceLoader.Provider::get)
                            .filter(npp -> npp.isValid() && npp.handleProtocol(proto))
                            .findFirst()
                            .orElse(null);
    }

    /**
     * Install the requested {@link NetworkProtocolProvider}, overwriting the previously installed. Checks are done to
     * ensure that the provided protocol is valid
     * @param protocol The {@link NetProtocol}
     * @param provider The {@link NetworkProtocolProvider} to be installed.
     * @throws IllegalArgumentException if the provider is not usable for this protocol
     */
    public static <S extends SocketAddress> void install(NetProtocol protocol, NetworkProtocolProvider<S> provider)
    {
        if (provider.isValid() && provider.handleProtocol(protocol)) {
            providers.put(protocol, provider);
        }
        else {
            throw new IllegalArgumentException("The given provider can't handle " + protocol);
        }
    }

    public static <S extends SocketAddress> NetProtocol findByAddress(S socketAddress)
    {
        for (Map.Entry<NetProtocol, NetworkProtocolProvider<? extends SocketAddress>> e : providers.entrySet()) {
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

    public <S extends SocketAddress> void resolve(Address<S> paddr, boolean ipv6)
    {
        // TODO V4 init address for pgm & epgm
    }

    @SuppressWarnings("unchecked")
    public <S extends SocketAddress> IZAddress<S> zresolve(String addr, boolean ipv6)
    {
        return (IZAddress<S>) resolve().zresolve(addr, ipv6);
    }

    @SuppressWarnings("unchecked")
    public <S extends SocketAddress> void startConnecting(Options options, IOThread ioThread, SessionBase session, Address<S> addr,
                                         boolean delayedStart, Consumer<Own> launchChild, BiConsumer<SessionBase, IEngine> sendAttach)
    {
        resolve().startConnecting(options, ioThread, session, (Address<SocketAddress>) addr, delayedStart, launchChild, sendAttach);
    }

    @SuppressWarnings("unchecked")
    private <S extends SocketAddress> NetworkProtocolProvider<S> resolve()
    {
        NetworkProtocolProvider<S> protocolProvider = (NetworkProtocolProvider<S>) providers.computeIfAbsent(this, NetProtocol::resolveProtocol);
        if (protocolProvider == null || ! protocolProvider.isValid()) {
            throw new IllegalArgumentException("Unsupported network protocol " + this);
        }
        return protocolProvider;
    }

    public String formatSocketAddress(SocketAddress socketAddress)
    {
        return resolve().formatSocketAddress(socketAddress);
    }

    public boolean wantsIOThread()
    {
        return resolve().wantsIOThread();
    }
}
