package zmq;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class SocketBase extends Own 
    implements IPollEvents, Pipe.IPipeEvents {
    
    //typedef std::multimap <std::string, own_t *> endpoints_t;
    final Map<String, Own> endpoints;

    //  Used to check whether the object is a socket.
    int tag;

    //  If true, associated context was already terminated.
    boolean ctx_terminated;

    //  If true, object should have been already destroyed. However,
    //  destruction is delayed while we unwind the stack to the point
    //  where it doesn't intersect the object being destroyed.
    boolean destroyed;

    //  Socket's mailbox object.
    final Mailbox mailbox;

    //  List of attached pipes.
    //typedef array_t <pipe_t, 3> pipes_t;
    final List<Pipe> pipes;

    //  Reaper's poller and handle of this socket within it.
    Poller poller;
    SelectableChannel handle;

    //  Timestamp of when commands were processed the last time.
    long last_tsc;

    //  Number of messages received since last command processing.
    int ticks;

    //  True if the last message received had MORE flag set.
    boolean rcvmore;

    //  Improves efficiency of time measurement.
    final Clock clock;

    final Lock sync;
    
    private final String name;
    private final String id;
    
    SocketBase (Ctx parent_, int tid_, int sid_) 
    {
        super (parent_, tid_);
        tag = 0xbaddecaf;
        ctx_terminated = false;
        destroyed = false;
        last_tsc = 0;
        ticks = 0;
        rcvmore = false;
        
        options.socket_id = sid_;
        
        endpoints = new HashMap<String, Own>();
        sync = new ReentrantLock();
        clock = new Clock();
        pipes = new ArrayList<Pipe>();
        
        id = "" + tid_ + "." + sid_;
        name = "socket-" + id;
        mailbox = new Mailbox(name);
    }
    
    abstract protected void xattach_pipe (Pipe pipe_, boolean icanhasall_);
    abstract protected void xterminated(Pipe pipe_);
    protected void xwrite_activated(Pipe pipe_) {
        throw new UnsupportedOperationException("Must Override");
    }
    protected void xsetsockopt(int option_, int optval_) {};
    
    public void write_activated (Pipe pipe_)
    {
        xwrite_activated (pipe_);
    }
    
    boolean check_tag ()
    {
        return tag == 0xbaddecaf;
    }
    
    public String id() {
        return id;
    }

    public static SocketBase create (int type_, Ctx parent_,
        int tid_, int sid_)
    {
        SocketBase s = null;
        switch (type_) {

        case ZMQ.ZMQ_PAIR:
            s = new Pair (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_PUB:
            s = new Pub (parent_, tid_, sid_);
            break;
            /*
        case ZMQ.ZMQ_SUB:
            s = new Sub (parent_, tid_, sid_);
            break;
            */
        case ZMQ.ZMQ_REQ:
            s = new Req (parent_, tid_, sid_);
            break;
            /*
        case ZMQ.ZMQ_REP:
            s = new Rep (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_DEALER:
            s = new Dealer (parent_, tid_, sid_);
            break;
            */
        case ZMQ.ZMQ_ROUTER:
            s = new Router (parent_, tid_, sid_);
            break;     
        case ZMQ.ZMQ_PULL:
            s = new Pull (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_PUSH:
            s = new Push (parent_, tid_, sid_);
            break;
            /*
        case ZMQ.ZMQ_XPUB:
            s = new XPub (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_XSUB:
            s = new XSub (parent_, tid_, sid_);
            break;
        */
        default:
            throw new IllegalArgumentException("type=" + type_);
        }
        //alloc_assert (s);
        return s;
    }

    public boolean send (Msg msg_, int flags_)
    {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }
        
        //  Check whether message passed to the function is valid.
        if (msg_ == null || !msg_.check ()) {
            throw new IllegalArgumentException(msg_.toString());
        }

        //  Process pending commands, if any.
        boolean brc = process_commands (0, true);
        if (!brc)
            return false;

        //  Clear any user-visible flags that are set on the message.
        msg_.reset_flags (Msg.more);

        //  At this point we impose the flags on the message.
        if ((flags_ & ZMQ.ZMQ_SNDMORE) > 0)
            msg_.set_flags (Msg.more);

        //  Try to send the message.
        brc = xsend (msg_, flags_);
        if (brc)
            return true;

        //  In case of non-blocking send we'll simply propagate
        //  the error - including EAGAIN - up the stack.
        if ((flags_ & ZMQ.ZMQ_DONTWAIT) > 0 || options.sndtimeo == 0)
            return false;

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care. 
        int timeout = options.sndtimeo;
        long end = timeout < 0 ? 0 : (clock.now_ms () + timeout);

        //  Oops, we couldn't send the message. Wait for the next
        //  command, process it and try to send the message again.
        //  If timeout is reached in the meantime, return EAGAIN.
        while (true) {
            if (!process_commands (timeout, false) )
                return false;
            brc = xsend (msg_, flags_);
            if (brc)
                break;
            if (timeout > 0) {
                timeout = (int) (end - clock.now_ms ());
                if (timeout <= 0) {
                    return false;
                }
            }
        }
        return true;
    }
    boolean connect (String addr_)
    {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }

        //  Process pending commands, if any.
        boolean brc = process_commands (0, false);
        if (!brc)
            return false;

        //  Parse addr_ string.
        URI uri;
        try {
            uri = new URI(addr_);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        String protocol = uri.getScheme();
        String address = uri.getHost();
        if (uri.getPort() > 0) {
            address = address + ":" + uri.getPort();
        }

        check_protocol (protocol);

        if (protocol.equals("inproc")) {

            //  TODO: inproc connect is specific with respect to creating pipes
            //  as there's no 'reconnect' functionality implemented. Once that
            //  is in place we should follow generic pipe creation algorithm.

            //  Find the peer endpoint.
            Ctx.Endpoint peer = find_endpoint (addr_);
            if (peer.socket == null)
                return false;
            // The total HWM for an inproc connection should be the sum of
            // the binder's HWM and the connector's HWM.
            int  sndhwm;
            int  rcvhwm;
            if (options.sndhwm == 0 || peer.options.rcvhwm == 0)
                sndhwm = 0;
            else
                sndhwm = options.sndhwm + peer.options.rcvhwm;
            if (options.rcvhwm == 0 || peer.options.sndhwm == 0)
                rcvhwm = 0;
            else
                rcvhwm = options.rcvhwm + peer.options.sndhwm;

            //  Create a bi-directional pipe to connect the peers.
            ZObject[] parents = {this, peer.socket};
            Pipe[] pipes = {null, null};
            int[] hwms = {sndhwm, rcvhwm};
            boolean[] delays = {options.delay_on_disconnect, options.delay_on_close};
            Pipe.pipepair (parents, pipes, hwms, delays);

            //  Attach local end of the pipe to this socket object.
            attach_pipe (pipes [0]);

            //  If required, send the identity of the local socket to the peer.
            if (options.send_identity) {
                Msg id = new Msg(options.identity_size);
                Utils.memcpy(id.data (), options.identity, options.identity_size);
                id.set_flags (Msg.identity);
                boolean written = pipes [0].write (id);
                assert (written);
                pipes [0].flush ();
            }

            //  If required, send the identity of the peer to the local socket.
            if (peer.options.send_identity) {
                Msg id = new Msg(peer.options.identity_size);
                Utils.memcpy (id.data (), peer.options.identity, options.identity_size);
                id.set_flags (Msg.identity);
                boolean written = pipes [1].write (id);
                assert (written);
                pipes [1].flush ();
            }

            //  Attach remote end of the pipe to the peer socket. Note that peer's
            //  seqnum was incremented in find_endpoint function. We don't need it
            //  increased here.
            send_bind (peer.socket, pipes [1], false);

            // Save last endpoint URI
            options.last_endpoint = addr_;

            return true;
        }

        //  Choose the I/O thread to run the session in.
        IOThread io_thread = choose_io_thread (options.affinity);
        if (io_thread == null) {
            throw new IllegalStateException("Empty IO Thread");
        }
        poller = io_thread.get_poller();
        Address paddr = new Address (protocol, address);

        //  Resolve address (if needed by the protocol)
        if (protocol.equals("tcp")) {
            paddr.resolved( new  TcpAddress () );
            paddr.resolved().resolve (
                address, options.ipv4only != 0 ? true : false);
        } else if(protocol.equals("ipc")) {
            paddr.resolved( new IpcAddress () );
            //alloc_assert (paddr.resolved.ipc_addr);
            paddr.resolved().resolve (address, true);
        }
        //  Create session.
        SessionBase session = SessionBase.create (io_thread, true, this,
            options, paddr);
        Errno.errno_assert (session != null);

        //  Create a bi-directional pipe.
        ZObject[] parents = {this, session};
        Pipe[] pipes = {null, null};
        int[] hwms = {options.sndhwm, options.rcvhwm};
        boolean[] delays = {options.delay_on_disconnect, options.delay_on_close};
        Pipe.pipepair (parents, pipes, hwms, delays);

        //  PGM does not support subscription forwarding; ask for all data to be
        //  sent to this pipe.
        boolean icanhasall = false;
        if (protocol.equals("pgm") || protocol.equals("epgm"))
            icanhasall = true;

        //  Attach local end of the pipe to the socket object.
        attach_pipe (pipes [0], icanhasall);

        //  Attach remote end of the pipe to the session object later on.
        session.attach_pipe (pipes [1]);

        // Save last endpoint URI
        options.last_endpoint = paddr.toString ();

        add_endpoint (addr_, session);
        return true;
    }

    boolean process_commands (int timeout_, boolean throttle_)
    {
        Command cmd;
        if (timeout_ != 0) {

            //  If we are asked to wait, simply ask mailbox to wait.
            cmd = mailbox.recv (timeout_);
        }
        else {

            //  If we are asked not to wait, check whether we haven't processed
            //  commands recently, so that we can throttle the new commands.

            //  Get the CPU's tick counter. If 0, the counter is not available.
            long tsc = Clock.rdtsc ();

            //  Optimised version of command processing - it doesn't have to check
            //  for incoming commands each time. It does so only if certain time
            //  elapsed since last command processing. Command delay varies
            //  depending on CPU speed: It's ~1ms on 3GHz CPU, ~2ms on 1.5GHz CPU
            //  etc. The optimisation makes sense only on platforms where getting
            //  a timestamp is a very cheap operation (tens of nanoseconds).
            if (tsc != 0 && throttle_) {

                //  Check whether TSC haven't jumped backwards (in case of migration
                //  between CPU cores) and whether certain time have elapsed since
                //  last command processing. If it didn't do nothing.
                if (tsc >= last_tsc && tsc - last_tsc <= Config.max_command_delay.getValue())
                    return true;
                last_tsc = tsc;
            }

            //  Check whether there are any commands pending for this thread.
            cmd = mailbox.recv (0);
        }

        //  Process all the commands available at the moment.
        while (true) {
            if (cmd == null)
                break;
            cmd.destination().process_command (cmd);
            cmd = mailbox.recv (0);
         }
        if (ctx_terminated) {
            return false;
        }

        return true;
    }

    @Override
    protected void process_bind (Pipe pipe_)
    {       
        attach_pipe (pipe_);
    }       


    void check_protocol (String protocol_)
    {
        //  First check out whether the protcol is something we are aware of.
        if (!protocol_.equals("inproc") && !protocol_.equals("ipc") && !protocol_.equals("tcp") &&
              !protocol_.equals("pgm") && !protocol_.equals("epgm")) {
            throw new IllegalArgumentException(protocol_);
        }

        //  Check whether socket type and transport protocol match.
        //  Specifically, multicast protocols can't be combined with
        //  bi-directional messaging patterns (socket types).
        if ((protocol_.equals("pgm") || protocol_.equals("epgm")) &&
              options.type != ZMQ.ZMQ_PUB && options.type != ZMQ.ZMQ_SUB &&
              options.type != ZMQ.ZMQ_XPUB && options.type != ZMQ.ZMQ_XSUB) {
            throw new IllegalArgumentException(protocol_ + ",type=" + options.type);
        }

        //  Protocol is available.
    }
    
    void attach_pipe (Pipe pipe_) {
        attach_pipe(pipe_, false);
    }
    
    void attach_pipe (Pipe pipe_, boolean icanhasall_)
    {
        //  First, register the pipe so that we can terminate it later on.
        pipe_.set_event_sink (this);
        pipes.add (pipe_);

        //  Let the derived socket type know about new pipe.
        xattach_pipe (pipe_, icanhasall_);

        //  If the socket is already being closed, ask any new pipes to terminate
        //  straight away.
        if (is_terminating ()) {
            register_term_acks (1);
            pipe_.terminate (false);
        }
    }

    @Override
    public void in_event() {
        //  This function is invoked only once the socket is running in the context
        //  of the reaper thread. Process any commands from other threads/sockets
        //  that may be available at the moment. Ultimately, the socket will
        //  be destroyed.
        process_commands (0, false);
        check_destroy ();
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

    public Mailbox get_mailbox() {
        return mailbox;
    }
    
    void add_endpoint (String addr_, Own endpoint_)
    {
        //  Activate the session. Make it a child of this socket.
        launch_child (endpoint_);
        endpoints.put (addr_, endpoint_);
    }

    public int close() {
        //  Mark the socket as dead
        tag = 0xdeadbeef;
       
        //  Transfer the ownership of the socket from this application thread
        //  to the reaper thread which will take care of the rest of shutdown
        //  process.
        send_reap (this);

        return 0;
    }

    public void stop() {
        //  Called by ctx when it is terminated (zmq_term).
        //  'stop' command is sent from the threads that called zmq_term to
        //  the thread owning the socket. This way, blocking call in the
        //  owner thread can be interrupted.
        send_stop ();
    }

    public void start_reaping(Poller poller_) {
        
        // poller is io_thread now
        //poller.rm_fd(mailbox.get_fd ());
        
        //  Plug the socket to the reaper thread.
        poller = poller_;
        handle = poller.add_fd (mailbox.get_fd (), this);
        poller.set_pollin (mailbox.get_fd ());

        //  Initialise the termination and check whether it can be deallocated
        //  immediately.
        terminate ();
        check_destroy ();
    }
    
    void check_destroy ()
    {
        //  If the object was already marked as destroyed, finish the deallocation.
        if (destroyed) {

            //  Remove the socket from the reaper's poller.
            poller.rm_fd (handle);
            //  Remove the socket from the context.
            destroy_socket (this);

            //  Notify the reaper about the fact.
            send_reaped ();

            //  Deallocate.
            super.process_destroy ();
        }
    }

    public void monitor_event(int event, Object ... args) {
        get_ctx().monitor_event(this, event, args);
    }

    void process_destroy ()
    {
        destroyed = true;
    }
    
    protected void process_term (int linger_)
    {
        //  Unregister all inproc endpoints associated with this socket.
        //  Doing this we make sure that no new pipes from other sockets (inproc)
        //  will be initiated.
        unregister_endpoints (this);

        //  Ask all attached pipes to terminate.
        for (int i = 0; i != pipes.size (); ++i)
            pipes.get(i).terminate (false);
        register_term_acks (pipes.size ());

        //  Continue the termination process immediately.
        super.process_term (linger_);
    }
    
    protected void process_stop ()
    {
        //  Here, someone have called zmq_term while the socket was still alive.
        //  We'll remember the fact so that any blocking call is interrupted and any
        //  further attempt to use the socket will return ETERM. The user is still
        //  responsible for calling zmq_close on the socket though!
        ctx_terminated = true;
    }


    @Override
    public void terminated(Pipe pipe_) {
        //  Notify the specific socket type about the pipe termination.
        xterminated (pipe_);

        //  Remove the pipe from the list of attached pipes and confirm its
        //  termination if we are already shutting down.
        pipes.remove(pipe_);
        if (is_terminating ())
            unregister_term_ack ();

    }
    
    @Override
    public void read_activated(Pipe pipe) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + name + "]";
    }

    public void setsockopt(int option_, int optval_) {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }

        //  First, check whether specific socket type overloads the option.
        xsetsockopt (option_, optval_);

        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        options.setsockopt (option_, optval_);

    }
    
    public int getsockopt(int option_) {
        return getsockopt(option_, null);
    }
    
    public int getsockopt(int option_, ByteBuffer ret) {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }

        if (option_ == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }
        
        if (option_ == ZMQ.ZMQ_FD) {
            throw new UnsupportedOperationException();
        }
        
        if (option_ == ZMQ.ZMQ_EVENTS) {
            boolean rc = process_commands (0, false);
            if (!rc)
                return -1;
            int val = 0;
            if (has_out ())
                val |= ZMQ.ZMQ_POLLOUT;
            if (has_in ())
                val |= ZMQ.ZMQ_POLLIN;
            return val;
        }
        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        return options.getsockopt (option_, ret);

    }

    private boolean has_out() {
        return xhas_out ();
    }

    private boolean has_in() {
        return xhas_in ();
    }

    protected void xread_activated(Pipe pipe_) {
        throw new UnsupportedOperationException("Must Override");
    }

    public Msg xrecv(int flags_) {
        throw new UnsupportedOperationException("Must Override");
    }
    
    public boolean xsend(Msg msg_, int flags_) {
        throw new UnsupportedOperationException("Must Override");
    }

    protected boolean xhas_in() {
        throw new UnsupportedOperationException("Must Override");
    }
    protected boolean xhas_out() {
        throw new UnsupportedOperationException("Must Override");
    }

    public int bind(final String addr_) {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }

        //  Process pending commands, if any.
        boolean brc = process_commands (0, false);
        if (!brc)
            return -1;

        //  Parse addr_ string.
        URI uri;
        try {
            uri = new URI(addr_);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        String protocol = uri.getScheme();
        String address = uri.getHost();
        String path = uri.getPath();
        if (uri.getPort() > 0) {
            address = address + ":" + uri.getPort();
        }


        check_protocol (protocol);

        if (protocol.equals("inproc")) {
            Ctx.Endpoint endpoint = new Ctx.Endpoint(this, options);
            register_endpoint (addr_, endpoint);
            // Save last endpoint URI
            options.last_endpoint = addr_;
            return 0;
        }
        if (protocol.equals("pgm") || protocol.equals("epgm")) {
            //  For convenience's sake, bind can be used interchageable with
            //  connect for PGM and EPGM transports.
            return connect (addr_)?0:-1;
        }

        //  Remaining trasnports require to be run in an I/O thread, so at this
        //  point we'll choose one.
        IOThread io_thread = choose_io_thread (options.affinity);
        if (io_thread == null) {
            throw new IllegalStateException("Empty IO Thread");
        }

        if (protocol.equals("tcp")) {
            TcpListener listener = new TcpListener (
                io_thread, this, options);
            try {
                listener.set_address (address);
            } catch (IOException e) {
                listener.close();
                monitor_event (ZMQ.ZMQ_EVENT_BIND_FAILED, addr_, e);
                return -1;
            }

            // Save last endpoint URI
            options.last_endpoint = listener.get_address ();

            add_endpoint (addr_, listener);
            return 0;
        }

        if (protocol.equals("ipc")) {
            IpcListener listener = new IpcListener (
                io_thread, this, options);
            try {
                listener.set_address (path);
            } catch (IOException e) {
                listener.close();
                monitor_event (ZMQ.ZMQ_EVENT_BIND_FAILED, addr_, e);
                return -1;
            }

            // Save last endpoint URI
            options.last_endpoint = listener.get_address ();

            add_endpoint (addr_, listener);
            return 0;
        }

        assert (false);
        return -1;
    }

    public Msg recv(int flags_) {
        if (ctx_terminated) {
            throw new IllegalStateException();
        }
        
        //  Check whether message passed to the function is valid.
        //if (msg_ == null || !msg_.check ()) {
        //    throw new IllegalArgumentException(msg_.toString());
        //}
        
        //  Get the message.
        Msg msg_ = xrecv (flags_);
        //if (rc != 0)
        //    return false;

        //  Once every inbound_poll_rate messages check for signals and process
        //  incoming commands. This happens only if we are not polling altogether
        //  because there are messages available all the time. If poll occurs,
        //  ticks is set to zero and thus we avoid this code.
        //
        //  Note that 'recv' uses different command throttling algorithm (the one
        //  described above) from the one used by 'send'. This is because counting
        //  ticks is more efficient than doing RDTSC all the time.
        if (++ticks == Config.inbound_poll_rate.getValue()) {
            if (!process_commands (0, false))
                return null;
            ticks = 0;
        }

        //  If we have the message, return immediately.
        if (msg_ != null) {
            extract_flags (msg_);
            return msg_;
        }

        //  If the message cannot be fetched immediately, there are two scenarios.
        //  For non-blocking recv, commands are processed in case there's an
        //  activate_reader command already waiting int a command pipe.
        //  If it's not, return EAGAIN.
        if ((flags_ & ZMQ.ZMQ_DONTWAIT) > 0 || options.rcvtimeo == 0) {
            if (!process_commands (0, false))
                return null;
            ticks = 0;

            msg_ = xrecv (flags_);
            if (msg_ == null)
                return null;
            extract_flags (msg_);
            return msg_;
        }

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care. 
        int timeout = options.rcvtimeo;
        long end = timeout < 0 ? 0 : (clock.now_ms () + timeout);

        //  In blocking scenario, commands are processed over and over again until
        //  we are able to fetch a message.
        boolean block = (ticks != 0);
        while (true) {
            if (!process_commands (block ? timeout : 0, false))
                return null;
            msg_ = xrecv (flags_);
            if (msg_ != null) {
                ticks = 0;
                break;
            }
            block = true;
            if (timeout > 0) {
                timeout = (int) (end - clock.now_ms ());
                if (timeout <= 0) {
                    return null;
                }
            }
        }

        extract_flags (msg_);
        return msg_;

    }

    private void extract_flags(Msg msg_) {
        //  Test whether IDENTITY flag is valid for this socket type.
        if ((msg_.flags () & Msg.identity) > 0)
            assert (options.recv_identity);

        //  Remove MORE flag.
        rcvmore = msg_.has_more();
    }
}
