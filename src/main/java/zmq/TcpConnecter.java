package zmq;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

//  If 'delay' is true connecter first waits for a while, then starts
//  connection process.
public class TcpConnecter extends Own implements IPollEvents
{
    //  ID of the timer used to delay the reconnection.
    private static final int RECONNECT_TIMER_ID = 1;

    private final IOObject ioObject;

    //  Address to connect to. Owned by session_base_t.
    private final Address addr;

    //  Underlying socket.
    private SocketChannel handle;

    //  If true file descriptor is registered with the poller and 'handle'
    //  contains valid value.
    private boolean handleValid;

    //  If true, connecter is waiting a while before trying to connect.
    private final boolean delayedStart;

    //  True iff a timer has been started.
    private boolean timerStarted;

    //  Reference to the session we belong to.
    private final SessionBase session;

    //  Current reconnect ivl, updated for backoff strategy
    private int currentReconnectIvl;

    // String representation of endpoint to connect to
    private final Address address;

    // Socket
    private final SocketBase socket;

    public TcpConnecter(IOThread ioThread,
      SessionBase session, final Options options,
      final Address addr, boolean delayedStart)
    {
        super(ioThread, options);
        ioObject = new IOObject(ioThread);
        this.addr = addr;
        handle = null;
        handleValid = false;
        this.delayedStart = delayedStart;
        timerStarted = false;
        this.session = session;
        currentReconnectIvl = this.options.reconnectIvl;

        assert (this.addr != null);
        address = this.addr;
        socket = session.getSocket();
    }

    public void destroy()
    {
        assert (!timerStarted);
        assert (!handleValid);
        assert (handle == null);
    }

    @Override
    protected void processPlug()
    {
        ioObject.setHandler(this);
        if (delayedStart) {
            addreconnectTimer();
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

        if (handleValid) {
            ioObject.removeHandle(handle);
            handleValid = false;
        }

        if (handle != null) {
            close();
        }

        super.processTerm(linger);
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
    public void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectEvent()
    {
        boolean err = false;
        SocketChannel fd = null;
        try {
            fd = connect();
        }
        catch (ConnectException e) {
            err = true;
        }
        catch (SocketException e) {
            err = true;
        }
        catch (SocketTimeoutException e) {
            err = true;
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }

        ioObject.removeHandle(handle);
        handleValid = false;

        if (err) {
            //  Handle the error condition by attempt to reconnect.
            close();
            addreconnectTimer();
            return;
        }

        handle = null;

        try {
            Utils.tuneTcpSocket(fd);
            Utils.tuneTcpKeepalives(fd, options.tcpKeepAlive, options.tcpKeepAliveCnt, options.tcpKeepAliveIdle, options.tcpKeepAliveIntvl);
        }
        catch (SocketException e) {
            throw new RuntimeException(e);
        }

        //  Create the engine object for this connection.
        StreamEngine engine = null;
        try {
            engine = new StreamEngine(fd, options, address.toString());
        }
        catch (ZError.InstantiationException e) {
            socket.eventConnectDelayed(address.toString(), -1);
            return;
        }

        //  Attach the engine to the corresponding session object.
        sendAttach(session, engine);

        //  Shut the connecter down.
        terminate();

        socket.eventConnected(address.toString(), fd);
    }

    @Override
    public void timerEvent(int id)
    {
        timerStarted = false;
        startConnecting();
    }

    //  Internal function to start the actual connection establishment.
    private void startConnecting()
    {
        //  Open the connecting socket.

        try {
            boolean rc = open();

            //  Connect may succeed in synchronous manner.
            if (rc) {
                ioObject.addHandle(handle);
                handleValid = true;
                ioObject.connectEvent();
            }

            //  Connection establishment may be delayed. Poll for its completion.
            else {
                ioObject.addHandle(handle);
                handleValid = true;
                ioObject.setPollConnect(handle);
                socket.eventConnectDelayed(address.toString(), -1);
            }
        }
        catch (IOException e) {
            //  Handle any other error condition by eventual reconnect.
            if (handle != null) {
                close();
            }
            addreconnectTimer();
        }
    }

    //  Internal function to add a reconnect timer
    private void addreconnectTimer()
    {
        int rcIvl = getNewReconnectIvl();
        ioObject.addTimer(rcIvl, RECONNECT_TIMER_ID);

        // resolve address again to take into account other addresses
        // besides the failing one (e.g. multiple dns entries).
        try {
            address.resolve();
        }
        catch (Exception ignored) {
            // This will fail if the network goes away and the
            // address cannot be resolved for some reason. Try
            // not to fail as the event loop will quit
        }

        socket.eventConnectRetried(address.toString(), rcIvl);
        timerStarted = true;
    }

    //  Internal function to return a reconnect backoff delay.
    //  Will modify the currentReconnectIvl used for next call
    //  Returns the currently used interval
    private int getNewReconnectIvl()
    {
        //  The new interval is the current interval + random value.
        int thisInterval = currentReconnectIvl +
            (Utils.generateRandom() % options.reconnectIvl);

        //  Only change the current reconnect interval  if the maximum reconnect
        //  interval was set and if it's larger than the reconnect interval.
        if (options.reconnectIvlMax > 0 &&
            options.reconnectIvlMax > options.reconnectIvl) {
            //  Calculate the next interval
            currentReconnectIvl = currentReconnectIvl * 2;
            if (currentReconnectIvl >= options.reconnectIvlMax) {
                currentReconnectIvl = options.reconnectIvlMax;
            }
        }
        return thisInterval;
    }

    //  Open TCP connecting socket. Returns -1 in case of error,
    //  true if connect was successfull immediately. Returns false with
    //  if async connect was launched.
    private boolean open() throws IOException
    {
        assert (handle == null);

        //  Create the socket.
        handle = SocketChannel.open();

        // Set the socket to non-blocking mode so that we get async connect().
        Utils.unblockSocket(handle);

        //  Connect to the remote peer.
        if (addr == null) {
            throw new IOException("Null address");
        }

        Address.IZAddress resolved = addr.resolved();
        if (resolved == null) {
            throw new IOException("Address not resolved");
        }

        SocketAddress sa = resolved.address();
        if (sa == null) {
            throw new IOException("Socket address not resolved");
        }

        boolean rc = false;
        try {
            rc = handle.connect(sa);
        }
        catch (IllegalArgumentException e) {
            // this will happen if sa is bad.  Address validation is not documented but
            // I've found that IAE is thrown in openjdk as well as on android.
            throw new IOException(e.getMessage(), e);
        }

        return rc;

    }

    //  Get the file descriptor of newly created connection. Returns
    //  retired_fd if the connection was unsuccessfull.
    private SocketChannel connect() throws IOException
    {
        boolean finished = handle.finishConnect();
        assert finished;
        SocketChannel ret = handle;

        return ret;
    }

    //  Close the connecting socket.
    private void close()
    {
        assert (handle != null);
        try {
            handle.close();
            socket.eventClosed(address.toString(), handle);
        }
        catch (IOException e) {
            socket.eventCloseFailed(address.toString(), ZError.exccode(e));
        }
        handle = null;
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + options.socketId + "]";
    }
}
