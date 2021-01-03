package zmq.io.net.ipc;

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

public class IpcNetworkProtocolProvider implements NetworkProtocolProvider {
    @Override
    public boolean handleProtocol(NetProtocol protocol) {
        return protocol == NetProtocol.ipc;
    }

    @Override
    public Listener getListener(IOThread ioThread, SocketBase socket,
                                Options options) {
        return new IpcListener(ioThread, socket, options);
    }

    @Override
    public IZAddress zresolve(String addr, boolean ipv6) {
        return new IpcAddress(addr);
    }

    @Override
    public void startConnecting(Options options, IOThread ioThread,
                                SessionBase session, Address addr,
                                boolean delayedStart, Consumer<Own> launchChild,
                                BiConsumer<SessionBase, IEngine> sendAttach) {
        IpcConnecter connecter = new IpcConnecter(ioThread, session, options, addr, delayedStart);
        launchChild.accept(connecter);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
