package zmq;

public class SessionBase extends Own implements Pipe.IPipeEvents {

    //  If true, this session (re)connects to the peer. Otherwise, it's
    //  a transient session created by the listener.
    boolean connect;
    
    //  Pipe connecting the session to its socket.
    Pipe pipe;

    //  This flag is true if the remainder of the message being processed
    //  is still in the in pipe.
    boolean incomplete_in;

    //  True if termination have been suspended to push the pending
    //  messages to the network.
    boolean pending;

    //  The protocol I/O engine connected to the session.
    IEngine engine;

    //  The socket the session belongs to.
    SocketBase socket;

    //  I/O thread the session is living in. It will be used to plug in
    //  the engines into the same thread.
    IOThread io_thread;

    //  ID of the linger timer
    private static int linger_timer_id = 0x20;

    //  True is linger timer is running.
    boolean has_linger_timer;

    //  If true, identity is to be sent/recvd from the network.
    boolean send_identity;
    boolean recv_identity;
    
    //  Protocol and address to use when connecting.
    final Address addr;

    IOObject io_object;

    private final String name ;
    
    public SessionBase(IOThread io_thread_, boolean connect_,
            SocketBase socket_, Options options_, Address addr_) {
        super(io_thread_, options_);
        io_object = new IOObject(io_thread_);
        
        connect = connect_;
        pipe = null;
        incomplete_in = false;
        pending = false;
        engine = null;
        socket = socket_;
        io_thread = io_thread_;
        has_linger_timer = false;
        send_identity = options_.send_identity;
        recv_identity = options_.recv_identity;
        addr = addr_;
        
        name = "session-" + socket_.id();
    }

    public static SessionBase create(IOThread io_thread_, boolean connect_,
            SocketBase socket_, Options options_, Address addr_) {
        
        SessionBase s = null;
        switch (options_.type) {
        /*
        case ZMQ_REQ:
            s = new (std::nothrow) req_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_DEALER:
            s = new (std::nothrow) dealer_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_REP:
            s = new (std::nothrow) rep_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_ROUTER:
            s = new (std::nothrow) router_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        */
        case ZMQ.ZMQ_PUB:
            s = new Pub.PubSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        /*
        case ZMQ_XPUB:
            s = new (std::nothrow) xpub_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_SUB:
            s = new (std::nothrow) sub_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_XSUB:
            s = new (std::nothrow) xsub_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
            */
        case ZMQ.ZMQ_PUSH:
            s = new Push.PushSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
            /*
        case ZMQ_PULL:
            s = new (std::nothrow) pull_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ_PAIR:
            s = new (std::nothrow) pair_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        */
        default:
            throw new IllegalArgumentException("type=" + options_.type);
        }
        return s;
    }

    public void attach_pipe(Pipe pipe_) {
        assert (!is_terminating ());
        assert (pipe == null);
        assert (pipe_ != null);
        pipe = pipe_;
        pipe.set_event_sink (this);
    }
    
    protected void process_plug ()
    {
        if (connect)
            start_connecting (false);
    }

    private void start_connecting (boolean wait_)
    {
        assert (connect);

        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        IOThread io_thread = choose_io_thread (options.affinity);
        assert (io_thread != null);

        //  Create the connecter object.

        if (addr.protocol().equals("tcp")) {
            TcpConnecter connecter = new TcpConnecter (
                io_thread, this, options, addr, wait_);
            //alloc_assert (connecter);
            launch_child (connecter);
            return;
        }
        
        if (addr.protocol().equals("ipc")) {
            IpcConnecter connecter = new IpcConnecter (
                io_thread, this, options, addr, wait_);
            //alloc_assert (connecter);
            launch_child (connecter);
            return;
        }
        
        assert (false);
    }
    
    public void monitor_event (int event_, Object ... args)
    {
        socket.monitor_event (event_, args);
    }

    protected void process_term (int linger_)
    {
        assert (!pending);

        //  If the termination of the pipe happens before the term command is
        //  delivered there's nothing much to do. We can proceed with the
        //  stadard termination immediately.
        if (pipe == null) {
            proceed_with_term ();
            return;
        }

        pending = true;

        //  If there's finite linger value, delay the termination.
        //  If linger is infinite (negative) we don't even have to set
        //  the timer.
        if (linger_ > 0) {
            assert (!has_linger_timer);
            io_object.add_timer (linger_, linger_timer_id);
            has_linger_timer = true;
        }

        //  Start pipe termination process. Delay the termination till all messages
        //  are processed in case the linger time is non-zero.
        pipe.terminate (linger_ != 0);

        //  TODO: Should this go into pipe_t::terminate ?
        //  In case there's no engine and there's only delimiter in the
        //  pipe it wouldn't be ever read. Thus we check for it explicitly.
        pipe.check_read ();
    }

    private void proceed_with_term() {
        //  The pending phase have just ended.
        pending = false;

        //  Continue with standard termination.
        super.process_term (0);
    }

    @Override
    public void terminated(Pipe pipe_) {
        //  Drop the reference to the deallocated pipe.
        assert (pipe == pipe_);
        pipe = null;

        //  If we are waiting for pending messages to be sent, at this point
        //  we are sure that there will be no more messages and we can proceed
        //  with termination safely.
        if (pending)
            proceed_with_term ();
    }

    @Override
    public void read_activated(Pipe pipe) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + name + "]";
    }

}
