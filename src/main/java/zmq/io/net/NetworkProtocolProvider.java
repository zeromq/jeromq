package zmq.io.net;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address.IZAddress;

public interface NetworkProtocolProvider
{
    boolean handleProtocol(NetProtocol protocol);
    Listener getListener(IOThread ioThread, SocketBase socket, Options options);
    IZAddress zresolve(String addr, boolean ipv6);
    void startConnecting(Options options, IOThread ioThread, SessionBase session, Address addr, boolean delayedStart,
            Consumer<Own> launchChild, BiConsumer<SessionBase, IEngine> sendAttach);
    boolean isValid();
}
