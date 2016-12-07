package zmq;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpListener extends Own implements IPollEvents
{
    private static boolean isWindows;
    static
    {
        String os = System.getProperty("os.name").toLowerCase();
        isWindows = os.indexOf("win") >= 0;
    }

    //  Address to listen on.
    private final TcpAddress address;

    //  Underlying socket.
    private ServerSocketChannel handle;

    //  Socket the listerner belongs to.
    private SocketBase socket;

    // String representation of endpoint to bind to
    private String endpoint;

    private final IOObject ioObject;

    public TcpListener(IOThread ioThread, SocketBase socket, final Options options)
    {
        super(ioThread, options);

        ioObject = new IOObject(ioThread);
        address = new TcpAddress();
        handle = null;
        this.socket = socket;
    }

    @Override
    public void destroy()
    {
        assert (handle == null);
    }

    @Override
    protected void processPlug()
    {
        //  Start polling for incoming connections.
        ioObject.setHandler(this);
        ioObject.addHandle(handle);
        ioObject.setPollAccept(handle);
    }

    @Override
    protected void processTerm(int linger)
    {
        ioObject.setHandler(this);
        ioObject.removeHandle(handle);
        close();
        super.processTerm(linger);
    }

    @Override
    public void acceptEvent()
    {
        SocketChannel fd = null;

        try {
            fd = accept();
            Utils.tuneTcpSocket(fd);
            Utils.tuneTcpKeepalives(fd, options.tcpKeepAlive, options.tcpKeepAliveCnt, options.tcpKeepAliveIdle, options.tcpKeepAliveIntvl);
        }
        catch (IOException e) {
            //  If connection was reset by the peer in the meantime, just ignore it.
            //  TODO: Handle specific errors like ENFILE/EMFILE etc.
            socket.eventAcceptFailed(endpoint, ZError.exccode(e));
            return;
        }

        //  Create the engine object for this connection.
        StreamEngine engine = null;
        try {
            engine = new StreamEngine(fd, options, endpoint);
        }
        catch (ZError.InstantiationException e) {
            socket.eventAcceptFailed(endpoint, ZError.EINVAL);
            return;
        }
        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        IOThread ioThread = chooseIoThread(options.affinity);

        //  Create and launch a session object.
        SessionBase session = SessionBase.create(ioThread, false, socket,
            options, new Address(fd.socket().getRemoteSocketAddress()));
        session.incSeqnum();
        launchChild(session);
        sendAttach(session, engine, false);
        socket.eventAccepted(endpoint, fd);
    }

    //  Close the listening socket.
    private void close()
    {
        if (handle == null) {
            return;
        }

        try {
            handle.close();
            socket.eventClosed(endpoint, handle);
        }
        catch (IOException e) {
            socket.eventCloseFailed(endpoint, ZError.exccode(e));
        }
        handle = null;
    }

    public String getAddress()
    {
        return address.toString();
    }

    //  Set address to listen on.
    public int setAddress(final String addr)
    {
        address.resolve(addr, options.ipv4only > 0);
        try {
            handle = ServerSocketChannel.open();
            handle.configureBlocking(false);
            if (!isWindows) {
                handle.socket().setReuseAddress(true);
            }
            handle.socket().bind(address.address(), options.backlog);
        }
        catch (IOException e) {
            close();
            return ZError.EADDRINUSE;
        }
        endpoint = address.toString();
        socket.eventListening(endpoint, handle);
        return 0;
    }

    //  Accept the new connection. Returns the file descriptor of the
    //  newly created connection. The function may return retired_fd
    //  if the connection was dropped while waiting in the listen backlog
    //  or was denied because of accept filters.
    private SocketChannel accept()
    {
        Socket sock = null;
        try {
            sock = handle.socket().accept();
        }
        catch (IOException e) {
            return null;
        }

        if (!options.tcpAcceptFilters.isEmpty()) {
            boolean matched = false;
            for (TcpAddress.TcpAddressMask am : options.tcpAcceptFilters) {
                if (am.matchAddress(address.address())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                try {
                    sock.close();
                }
                catch (IOException e) {
                }
                return null;
            }
        }
        return sock.getChannel();
    }

    @Override
    public void inEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void outEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timerEvent(int id)
    {
        throw new UnsupportedOperationException();
    }
}
