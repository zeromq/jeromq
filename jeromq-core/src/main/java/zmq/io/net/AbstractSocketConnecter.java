package zmq.io.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.ZError;
import zmq.io.IOObject;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.StreamEngine;
import zmq.poll.IPollEvents;
import zmq.poll.Poller;
import zmq.util.Utils;

//  If 'delay' is true connecter first waits for a while, then starts
//  connection process.
public abstract class AbstractSocketConnecter<S extends SocketAddress> extends Own implements IPollEvents
{
    //  ID of the timer used to delay the reconnection.
    protected static final int RECONNECT_TIMER_ID = 1;

    protected final IOObject ioObject;

    //  Address to connect to. Owned by session_base_t.
    private final Address<S> addr;

    //  Underlying socket.
    private SocketChannel fd;
    private Poller.Handle handle;

    //  If true, connecter is waiting a while before trying to connect.
    protected final boolean delayedStart;

    //  True if a timer has been started.
    private boolean timerStarted;

    //  Reference to the session we belong to.
    private final SessionBase session;

    //  Current reconnect ivl, updated for backoff strategy
    private int currentReconnectIvl;

    // Socket
    private final SocketBase socket;

    public AbstractSocketConnecter(IOThread ioThread, SessionBase session, Options options, Address<S> addr,
            boolean delayedStart)
    {
        super(ioThread, options);

        assert (addr != null);

        ioObject = new IOObject(ioThread, this);
        this.addr = addr;
        fd = null;
        this.delayedStart = delayedStart;
        timerStarted = false;
        this.session = session;
        currentReconnectIvl = this.options.reconnectIvl;
        socket = session.getSocket();
    }

    @Override
    protected void destroy()
    {
        assert (!timerStarted);
        assert (handle == null);
        assert (fd == null);
        ioObject.unplug();
    }

    @Override
    protected void processPlug()
    {
        ioObject.plug();
        if (delayedStart) {
            addReconnectTimer();
        }
        else {
            startConnecting();
        }
    }

    @Override
    protected void processTerm(int linger)
    {
        if (timerStarted) {
            ioObject.cancelTimer(RECONNECT_TIMER_ID);
            timerStarted = false;
        }

        if (handle != null) {
            ioObject.removeHandle(handle);
            handle = null;
        }

        if (fd != null) {
            close();
        }

        super.processTerm(linger);
    }

    @Override
    public void connectEvent()
    {
        ioObject.removeHandle(handle);
        handle = null;

        SocketChannel channel = connect();

        if (channel == null) {
            //  Handle the error condition by attempt to reconnect.
            close();
            addReconnectTimer();
            return;
        }

        try {
            tuneConnectedChannel(channel);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        // remember our fd for ZMQ_SRCFD in messages
        //        socket.setFd(channel);

        //  Create the engine object for this connection.
        StreamEngine engine;
        try {
            engine = new StreamEngine(channel, options, addr.toString());
        }
        catch (ZError.InstantiationException e) {
            // TODO V4 socket.eventConnectDelayed(addr.toString(), -1);
            return;
        }

        this.fd = null;

        //  Attach the engine to the corresponding session object.
        sendAttach(session, engine);

        //  Shut the connecter down.
        terminate();

        socket.eventConnected(addr.toString(), channel);
    }

    @Override
    public void timerEvent(int id)
    {
        assert (id == RECONNECT_TIMER_ID);

        timerStarted = false;
        startConnecting();
    }

    //  Internal function to start the actual connection establishment.
    private void startConnecting()
    {
        //  Open the connecting socket.
        try {
            boolean rc = open();

            handle = ioObject.addFd(fd);
            //  Connect may succeed in synchronous manner.
            if (rc) {
                connectEvent();
            }
            //  Connection establishment may be delayed. Poll for its completion.
            else {
                ioObject.setPollConnect(handle);
                socket.eventConnectDelayed(addr.toString(), -1);
            }
        }
        catch (RuntimeException | IOException e) {
            //  Handle any other error condition by eventual reconnect.
            if (fd != null) {
                close();
            }
            addReconnectTimer();
        }
    }

    //  Internal function to add a reconnect timer
    private void addReconnectTimer()
    {
        int rcIvl = getNewReconnectIvl();
        ioObject.addTimer(rcIvl, RECONNECT_TIMER_ID);

        // resolve address again to take into account other addresses
        // besides the failing one (e.g. multiple dns entries).
        try {
            addr.resolve(options.ipv6);
        }
        catch (Exception ignored) {
            // This will fail if the network goes away and the
            // address cannot be resolved for some reason. Try
            // not to fail as the event loop will quit
        }

        socket.eventConnectRetried(addr.toString(), rcIvl);
        timerStarted = true;
    }

    //  Internal function to return a reconnect backoff delay.
    //  Will modify the currentReconnectIvl used for next call
    //  Returns the currently used interval
    private int getNewReconnectIvl()
    {
        //  The new interval is the current interval + random value.
        int interval = currentReconnectIvl + (Utils.randomInt() % options.reconnectIvl);

        //  Only change the current reconnect interval  if the maximum reconnect
        //  interval was set and if it's larger than the reconnect interval.
        if (options.reconnectIvlMax > 0 && options.reconnectIvlMax > options.reconnectIvl) {
            //  Calculate the next interval
            currentReconnectIvl = Math.min(currentReconnectIvl * 2, options.reconnectIvlMax);
        }
        return interval;
    }

    //  Open connecting socket.
    // Returns true if connect was successful immediately.
    // Returns false if async connect was launched.
    private boolean open() throws IOException
    {
        assert (fd == null);

        //  Resolve the address
        if (addr == null) {
            throw new IOException("Null address");
        }

        addr.resolve(options.ipv6);

        Address.IZAddress<S> resolved = addr.resolved();
        if (resolved == null) {
            throw new IOException("Address not resolved");
        }

        SocketAddress sa = resolved.address();
        if (sa == null) {
            throw new IOException("Socket address not resolved");
        }

        fd = openClient(resolved);

        // TODO V4 Set a source address for conversations
        //  Connect to the remote peer.
        boolean rc;
        try {
            rc = fd.connect(sa);
            if (! rc) {
                //  Translate error codes indicating asynchronous connect has been
                //  launched to a uniform EINPROGRESS.
                errno.set(ZError.EINPROGRESS);
            }
        }
        catch (IllegalArgumentException e) {
            // this will happen if sa is bad.  Address validation is not documented, but
            // I've found that IAE is thrown in openjdk as well as on android.
            throw new IOException(e.getMessage(), e);
        }

        return rc;
    }

    protected abstract SocketChannel openClient(Address.IZAddress<S> address) throws IOException;

    protected abstract void tuneConnectedChannel(SocketChannel channel) throws IOException;

    //  Get the file descriptor of newly created connection. Returns
    //  null if the connection was unsuccessful.
    private SocketChannel connect()
    {
        try {
            //  Async connect has finished. Check whether an error occurred
            boolean finished = fd.finishConnect();
            assert (finished);
            return fd;
        }
        catch (IOException e) {
            return null;
        }
    }

    //  Close the connecting socket.
    protected void close()
    {
        assert (fd != null);
        try {
            fd.close();
            socket.eventClosed(addr.toString(), fd);
        }
        catch (IOException e) {
            socket.eventCloseFailed(addr.toString(), ZError.exccode(e));
        }
        fd = null;
    }

    @Override
    public void inEvent()
    {
        // connected but attaching to stream engine is not completed. do nothing
    }

    @Override
    public void outEvent()
    {
        // connected but attaching to stream engine is not completed. do nothing
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "[" + options.socketId + "]";
    }
}
