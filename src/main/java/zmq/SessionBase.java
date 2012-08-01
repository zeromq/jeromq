package zmq;

public class SessionBase extends Own implements IPipeEvents {

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
        case ZMQ_PUSH:
            s = new (std::nothrow) push_session_t (io_thread_, connect_,
                socket_, options_, addr_);
            break;
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
            Errno.set(Errno.EINVAL);
            return null;
        }
        //alloc_assert (s);
        return s;
    }

    public void attach_pipe(Pipe pipe_) {
        assert (!is_terminating ());
        assert (pipe == null);
        assert (pipe_ != null);
        pipe = pipe_;
        pipe.set_event_sink (this);
    }

}
