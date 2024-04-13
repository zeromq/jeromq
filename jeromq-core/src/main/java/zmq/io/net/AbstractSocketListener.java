package zmq.io.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.io.IOObject;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.StreamEngine;
import zmq.poll.Poller;
import zmq.socket.Sockets;

public abstract class AbstractSocketListener<S extends SocketAddress, A extends Address.IZAddress<S>> extends Listener
{
    //  Address to listen on.
    private A address;

    //  Underlying socket.
    private ServerSocketChannel fd;
    private Poller.Handle       handle;

    // String representation of endpoint to bind to
    private String endpoint;

    private final IOObject ioObject;

    public AbstractSocketListener(IOThread ioThread, SocketBase socket, Options options)
    {
        super(ioThread, socket, options);

        ioObject = new IOObject(ioThread, this);
        fd = null;
    }

    protected ServerSocketChannel getFd()
    {
        return this.fd;
    }

    protected A getZAddress()
    {
        return this.address;
    }

    @Override
    public void destroy()
    {
        assert (fd == null);
        assert (handle == null);
        ioObject.unplug();
    }

    @Override
    protected void processPlug()
    {
        //  Start polling for incoming connections.
        ioObject.plug();
        handle = ioObject.addFd(fd);
        ioObject.setPollAccept(handle);
    }

    @Override
    protected void processTerm(int linger)
    {
        ioObject.removeHandle(handle);
        handle = null;
        close();
        super.processTerm(linger);
    }

    @Override
    public void acceptEvent()
    {
        SocketChannel channel;

        try {
            channel = accept(fd);

            //  If connection was reset by the peer in the meantime, just ignore it.
            if (channel == null) {
                socket.eventAcceptFailed(endpoint, ZError.EADDRNOTAVAIL);
                return;
            }
            tuneAcceptedChannel(channel);
        }
        catch (IOException e) {
            //  If connection was reset by the peer in the meantime, just ignore it.
            //  TODO: Handle specific errors like ENFILE/EMFILE etc.
            socket.eventAcceptFailed(endpoint, ZError.exccode(e));
            return;
        }

        // remember our fd for ZMQ_SRCFD in messages
        //        socket.setFd(channel);

        //  Create the engine object for this connection.
        StreamEngine engine;
        try {
            engine = new StreamEngine(channel, options, endpoint);
        }
        catch (ZError.InstantiationException e) {
            socket.eventAcceptFailed(endpoint, ZError.EINVAL);
            return;
        }

        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        IOThread ioThread = chooseIoThread(options.affinity);
        assert (ioThread != null);

        //  Create and launch a session object.
        SessionBase session = Sockets.createSession(ioThread, false, socket, options, null);
        assert (session != null);

        session.incSeqnum();
        launchChild(session);
        sendAttach(session, engine, false);
        socket.eventAccepted(endpoint, channel);
    }

    //  Close the listening socket.
    private void close()
    {
        assert (fd != null);

        try {
            closeServerChannel(fd);
            socket.eventClosed(endpoint, fd);
        }
        catch (IOException e) {
            socket.eventCloseFailed(endpoint, ZError.exccode(e));
        }
        fd = null;
    }

    protected abstract void closeServerChannel(ServerSocketChannel fd) throws IOException;

    protected boolean setZAddress(A address)
    {
        this.address = address;
        endpoint = address.toString();

        //  Create a listening socket.
        try {
            fd = openServer(address);
            bindServer(fd, address);

            // find the address in case of wildcard
            endpoint = getAddress();
        }
        catch (IOException e) {
            close();
            errno.set(ZError.EADDRINUSE);
            return false;
        }
        socket.eventListening(endpoint, fd);
        return true;
    }

    protected abstract ServerSocketChannel openServer(A address) throws IOException;

    protected abstract void bindServer(ServerSocketChannel fd, A address) throws IOException;

    //  Accept the new connection. Returns the file descriptor of the
    //  newly created connection. The function may throw IOException
    //  if the connection was dropped while waiting in the listen backlog
    //  or was denied because of accept filters.
    protected abstract SocketChannel accept(ServerSocketChannel fd) throws IOException;

    protected abstract void tuneAcceptedChannel(SocketChannel channel) throws IOException;

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "[" + options.socketId + "]";
    }
}
