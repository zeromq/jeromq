package zmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionBase extends Own implements Pipe.IPipeEvents, IPollEvents {

    Logger LOG = LoggerFactory.getLogger(SessionBase.class);
    
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
        case ZMQ.ZMQ_REQ:
            s = new  Req.ReqSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_DEALER:
            s = new Dealer.DealerSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_REP:
            s = new Rep.RepSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_ROUTER:
            s = new Router.RouterSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_PUB:
            s = new Pub.PubSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_XPUB:
            s = new XPub.XPubSession(io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_SUB:
            s = new  Sub.SubSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_XSUB:
            s = new XSub.XSubSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
            
        case ZMQ.ZMQ_PUSH:
            s = new Push.PushSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
        case ZMQ.ZMQ_PULL:
            s = new Pull.PullSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
            /*
        case ZMQ_PAIR:
            s = new Pair.PairSession (io_thread_, connect_,
                socket_, options_, addr_);
            break;
            */
        case ZMQ.ZMQ_PROXY:
            s = new Proxy.ProxySession(io_thread_, connect_, 
                    socket_, options_, addr_);
            return s;
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
        io_object.set_handler(this);
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
    public void read_activated(Pipe pipe_) {
        assert (pipe == pipe_);

        if (engine != null)
            engine.activate_out ();
        else
            pipe.check_read ();
    }
    
    @Override
    public void write_activated (Pipe pipe_)
    {
        assert (pipe == pipe_);

        if (engine != null)
            engine.activate_in ();
    }

    
    @Override
    public String toString() {
        return super.toString() + "[" + options.socket_id + "]";
    }

    public void flush() {
        if (pipe != null)
            pipe.flush ();
    }

    public void detach() {
        //  Engine is dead. Let's forget about it.
        engine = null;

        //  Remove any half-done messages from the pipes.
        clean_pipes ();

        //  Send the event to the derived class.
        detached ();

        //  Just in case there's only a delimiter in the pipe.
        if (pipe != null)
            pipe.check_read ();
    }

    private void detached() {
        //  Transient session self-destructs after peer disconnects.
        if (!connect) {
            terminate ();
            return;
        }

        reset ();

        //  Reconnect.
        if (options.reconnect_ivl != -1)
            start_connecting (true);

        //  For subscriber sockets we hiccup the inbound pipe, which will cause
        //  the socket object to resend all the subscriptions.
        if (pipe != null && (options.type == ZMQ.ZMQ_SUB || options.type == ZMQ.ZMQ_XSUB))
            pipe.hiccup ();

    }

    protected void reset() {
        //  Restore identity flags.
        send_identity = options.send_identity;
        recv_identity = options.recv_identity;
    }

    private void clean_pipes() {
        if (pipe != null) {

            //  Get rid of half-processed messages in the out pipe. Flush any
            //  unflushed messages upstream.
            pipe.rollback ();
            pipe.flush ();

            //  Remove any half-read message from the in pipe.
            while (incomplete_in) {
                Msg msg = read();
                if (msg == null) {
                    assert (!incomplete_in);
                    break;
                }
                msg.close ();
            }
        }
    }

    public Msg read() {
        
        Msg msg_ = null;
        
        //  First message to send is identity (if required).
        if (send_identity) {
            msg_ = new Msg(options.identity_size);
            msg_.put(options.identity, 0, options.identity_size);
            send_identity = false;
            incomplete_in = false;
            
            if (LOG.isDebugEnabled()) {
                LOG.debug(toString() +  " send identity " + msg_);
            }
            return msg_;
        }

        if (pipe == null || (msg_ = pipe.read ()) == null ) {
            return null;
        }
        incomplete_in = msg_.has_more();

        return msg_;

    }
    
    protected boolean write (Msg msg_)
    {
        //  First message to receive is identity (if required).
        if (recv_identity) {
            msg_.set_flags (Msg.identity);
            recv_identity = false;
        }
        
        
        if (pipe != null && pipe.write (msg_)) {
            return true;
        }

        return false;
    }

    @Override
    protected void process_attach (IEngine engine_)
    {
        assert (engine_ != null);

        //  Create the pipe if it does not exist yet.
        if (pipe == null && !is_terminating ()) {
            ZObject[] parents = {this, socket};
            Pipe[] pipes = {null, null};
            int[] hwms = {options.rcvhwm, options.sndhwm};
            boolean[] delays = {options.delay_on_close, options.delay_on_disconnect};
            Pipe.pipepair (parents, pipes, hwms, delays);

            //  Plug the local end of the pipe.
            pipes [0].set_event_sink (this);

            //  Remember the local end of the pipe.
            assert (pipe == null);
            pipe = pipes [0];

            //  Ask socket to plug into the remote end of the pipe.
            send_bind (socket, pipes [1]);
        }

        //  Plug in the engine.
        assert (engine == null);
        engine = engine_;
        engine.plug (io_thread, this);
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
    public void connect_event() {
        throw new UnsupportedOperationException();
        
    }

    @Override
    public void accept_event() {
        throw new UnsupportedOperationException();
        
    }

    @Override
    public void timer_event(int id_) {
        throw new UnsupportedOperationException();
    }


}
