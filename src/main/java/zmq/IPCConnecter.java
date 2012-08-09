package zmq;

import java.nio.channels.SelectableChannel;

public class IpcConnecter extends Own {
    
    private final int reconnect_timer_id = 1;
    
    final private IOObject io_object;
    
    //  Address to connect to. Owned by session_base_t.
    final private Address addr;

    //  Underlying socket.
    private SelectableChannel s;

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

    public IpcConnecter (IOThread io_thread_,
            SessionBase session_, final Options options_,
            final Address addr_, boolean wait_)  {
        
        super (io_thread_, options_);
        io_object = new IOObject (io_thread_);
        addr = addr_;
        s = null;
        handle_valid = false;
        wait = wait_;
        session = session_;
        current_reconnect_ivl = options.reconnect_ivl;
        
        assert (addr!=null);
        assert (addr.protocol().equals("ipc"));
        endpoint = addr.toString ();
    }
}
