package zmq;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

public class TcpConnecter extends Own implements IPollEvents {

    private final static int reconnect_timer_id = 1;
    
    final private IOObject io_object;
    
    //  Address to connect to. Owned by session_base_t.
    final private Address addr;

    //  Underlying socket.
    private SocketChannel s;

    //  Handle corresponding to the listening socket.
    private SelectableChannel handle;

    //  If true file descriptor is registered with the poller and 'handle'
    //  contains valid value.
    private boolean handle_valid;

    //  If true, connecter is waiting a while before trying to connect.
    private boolean wait;

    //  Reference to the session we belong to.
    private SessionBase session;

    //  Current reconnect ivl, updated for backoff strategy
    private int current_reconnect_ivl;

    // String representation of endpoint to connect to
    private String endpoint;
    
    public TcpConnecter (IOThread io_thread_,
      SessionBase session_, final Options options_,
      final Address addr_, boolean wait_) {
        
        super (io_thread_, options_);
        io_object = new IOObject(io_thread_);
        addr = addr_;
        s = null; 
        handle_valid = false;
        wait = wait_;
        session = session_;
        current_reconnect_ivl = options.reconnect_ivl;
        
        assert (addr != null);
        assert (addr.protocol().equals("tcp"));
        addr.toString (endpoint);
    }
    
    protected void process_plug ()
    {
        if (wait)
            add_reconnect_timer();
        else {
            start_connecting ();
        }
    }

    private void add_reconnect_timer()
    {
        int rc_ivl = get_new_reconnect_ivl();
        io_object.add_timer (rc_ivl, reconnect_timer_id);
        session.monitor_event (ZMQ.ZMQ_EVENT_CONNECT_RETRIED, endpoint, rc_ivl);
    }
    
    private void start_connecting ()
    {
        //  Open the connecting socket.
        io_object.set_handler(this);
        
        try {
            boolean rc = open ();
    
            //  Connect may succeed in synchronous manner.
            if (rc) {
                handle = io_object.add_fd (s);
                handle_valid = true;
                io_object.out_event ();
                return;
            }
    
            //  Connection establishment may be delayed. Poll for its completion.
            else {
                handle = io_object.add_fd (s);
                handle_valid = true;
                io_object.set_pollconnect (handle);
                session.monitor_event (ZMQ.ZMQ_EVENT_CONNECT_DELAYED, endpoint, Errno.get());
                return;
            }
        } catch (IOException e) {
            //  Handle any other error condition by eventual reconnect.
            close ();
            wait = true;
            add_reconnect_timer();
        }
    }

    
    private int get_new_reconnect_ivl ()
    {
        //  The new interval is the current interval + random value.
        int this_interval = current_reconnect_ivl +
            (Utils.generate_random () % options.reconnect_ivl);

        //  Only change the current reconnect interval  if the maximum reconnect
        //  interval was set and if it's larger than the reconnect interval.
        if (options.reconnect_ivl_max > 0 &&
            options.reconnect_ivl_max > options.reconnect_ivl) {

            //  Calculate the next interval
            current_reconnect_ivl = current_reconnect_ivl * 2;
            if(current_reconnect_ivl >= options.reconnect_ivl_max) {
                current_reconnect_ivl = options.reconnect_ivl_max;
            }
        }
        return this_interval;
    }
    
    private boolean open () throws IOException
    {
        assert (s == null);

        //  Create the socket.
        s = SocketChannel.open();

        // Set the socket to non-blocking mode so that we get async connect().
        Utils.unblock_socket(s);

        //  Connect to the remote peer.
        boolean rc = s.connect(addr.resolved().address());

        return rc;

    }
    
    private SocketChannel connect () throws IOException
    {
        boolean finished = s.finishConnect();
        assert finished;
        SocketChannel ret = s;
        s = null;
        
        return ret;
    }
    

    @Override
    public void in_event() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void out_event() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void accept_event() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void connect_event ()
    {
        boolean err = false;
        SocketChannel fd = null;
        try {
            fd = connect ();
        } catch (ConnectException e) {
            err = true;
        } catch (SocketException e) {
            err = true;
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }

        io_object.rm_fd (handle);
        handle_valid = false;

        if (err) {
            //  Handle the error condition by attempt to reconnect.
            close ();
            wait = true;
            add_reconnect_timer();
            return;
        }
        try {
            Utils.tune_tcp_socket (fd);
            Utils.tune_tcp_keepalives (fd, options.tcp_keepalive, options.tcp_keepalive_cnt, options.tcp_keepalive_idle, options.tcp_keepalive_intvl);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        //  Create the engine object for this connection.
        StreamEngine engine = new StreamEngine (fd.socket(), options, endpoint);
        //alloc_assert (engine);

        //  Attach the engine to the corresponding session object.
        send_attach (session, engine);

        //  Shut the connecter down.
        terminate ();

        session.monitor_event (ZMQ.ZMQ_EVENT_CONNECTED, endpoint, fd);
    }



    private void close() {
        assert (s != null);
        try {
            s.close();
            session.monitor_event (ZMQ.ZMQ_EVENT_CLOSED, endpoint, s);
            s = null;
        } catch (IOException e) {
            session.monitor_event (ZMQ.ZMQ_EVENT_CLOSE_FAILED, endpoint, e.getCause());
        }
        
    }

    @Override
    public void timer_event(int id_) {
        wait = false;
        start_connecting ();
    }
    
    

}
