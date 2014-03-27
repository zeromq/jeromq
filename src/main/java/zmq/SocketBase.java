/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2011 VMware, Inc.
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package zmq;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class SocketBase extends Own
    implements IPollEvents, Pipe.IPipeEvents {

    //  Map of open endpoints.
    private final Map<String, Own> endpoints;

    //  Map of open inproc endpoints.
    private final Map<String, Pipe> inprocs;

    //  Used to check whether the object is a socket.
    private int tag;

    //  If true, associated context was already terminated.
    private boolean ctx_terminated;

    //  If true, object should have been already destroyed. However,
    //  destruction is delayed while we unwind the stack to the point
    //  where it doesn't intersect the object being destroyed.
    private boolean destroyed;

    //  Socket's mailbox object.
    private final Mailbox mailbox;

    //  List of attached pipes.
    private final List<Pipe> pipes;

    //  Reaper's poller and handle of this socket within it.
    private Poller poller;
    private SelectableChannel handle;

    //  Timestamp of when commands were processed the last time.
    private long last_tsc;

    //  Number of messages received since last command processing.
    private int ticks;

    //  True if the last message received had MORE flag set.
    private boolean rcvmore;

    // Monitor socket
    private SocketBase monitor_socket;

    // Bitmask of events being monitored
    private int monitor_events;

    protected ValueReference<Integer> errno;

    protected SocketBase (Ctx parent_, int tid_, int sid_)
    {
        super (parent_, tid_);
        tag = 0xbaddecaf;
        ctx_terminated = false;
        destroyed = false;
        last_tsc = 0;
        ticks = 0;
        rcvmore = false;
        monitor_socket = null;
        monitor_events = 0;

        options.socket_id = sid_;

        endpoints = new MultiMap<String, Own>();
        inprocs = new MultiMap<String, Pipe>();
        pipes = new ArrayList<Pipe>();

        mailbox = new Mailbox("socket-" + sid_);

        errno = new ValueReference<Integer>(0);
    }

    //  Concrete algorithms for the x- methods are to be defined by
    //  individual socket types.
    abstract protected void xattach_pipe (Pipe pipe_, boolean icanhasall_);
    abstract protected void xterminated(Pipe pipe_);


    //  Returns false if object is not a socket.
    public boolean check_tag ()
    {
        return tag == 0xbaddecaf;
    }


    //  Create a socket of a specified type.
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
        case ZMQ.ZMQ_SUB:
            s = new Sub (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_REQ:
            s = new Req (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_REP:
            s = new Rep (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_DEALER:
            s = new Dealer (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_ROUTER:
            s = new Router (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_PULL:
            s = new Pull (parent_, tid_, sid_);
            break;
        case ZMQ.ZMQ_PUSH:
            s = new Push (parent_, tid_, sid_);
            break;

        case ZMQ.ZMQ_XPUB:
            s = new XPub (parent_, tid_, sid_);
            break;

        case ZMQ.ZMQ_XSUB:
            s = new XSub (parent_, tid_, sid_);
            break;

        default:
            throw new IllegalArgumentException ("type=" + type_);
        }
        return s;
    }

    public void destroy () {
        stop_monitor ();
        assert (destroyed);
    }

    //  Returns the mailbox associated with this socket.
    public Mailbox get_mailbox () {
        return mailbox;
    }

    //  Interrupt blocking call if the socket is stuck in one.
    //  This function can be called from a different thread!
    public void stop() {
        //  Called by ctx when it is terminated (zmq_term).
        //  'stop' command is sent from the threads that called zmq_term to
        //  the thread owning the socket. This way, blocking call in the
        //  owner thread can be interrupted.
        send_stop ();

    }

    //  Check whether transport protocol, as specified in connect or
    //  bind, is available and compatible with the socket type.
    private void check_protocol (String protocol_)
    {
        //  First check out whether the protcol is something we are aware of.
        if (!protocol_.equals("inproc") && !protocol_.equals("ipc") && !protocol_.equals("tcp") /*&&
              !protocol_.equals("pgm") && !protocol_.equals("epgm")*/) {
            throw new UnsupportedOperationException (protocol_);
        }

        //  Check whether socket type and transport protocol match.
        //  Specifically, multicast protocols can't be combined with
        //  bi-directional messaging patterns (socket types).
        if ((protocol_.equals("pgm") || protocol_.equals("epgm")) &&
              options.type != ZMQ.ZMQ_PUB && options.type != ZMQ.ZMQ_SUB &&
              options.type != ZMQ.ZMQ_XPUB && options.type != ZMQ.ZMQ_XSUB) {
            throw new UnsupportedOperationException (protocol_ + ",type=" + options.type);
        }

        //  Protocol is available.
    }


    //  Register the pipe with this socket.
    private void attach_pipe (Pipe pipe_) {
        attach_pipe(pipe_, false);
    }

    private void attach_pipe (Pipe pipe_, boolean icanhasall_)
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

    public void setsockopt(int option_, Object optval_) {

        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  First, check whether specific socket type overloads the option.
        if (xsetsockopt(option_, optval_))
            return;

        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        options.setsockopt (option_, optval_);
    }

    public int getsockopt(int option_) {

        if (option_ != ZMQ.ZMQ_EVENTS && ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        // fast track to avoid boxing
        if (option_ == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }
        if (option_ == ZMQ.ZMQ_EVENTS) {
            boolean rc = process_commands (0, false);
            if (!rc && errno.get() == ZError.ETERM)
                return -1;
            assert (rc);
            int val = 0;
            if (has_out ())
                val |= ZMQ.ZMQ_POLLOUT;
            if (has_in ())
                val |= ZMQ.ZMQ_POLLIN;
            return val;
        }

        return (Integer) getsockoptx(option_);
    }

    public Object getsockoptx(int option_) {
        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        if (option_ == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }

        if (option_ == ZMQ.ZMQ_FD) {
            return mailbox.get_fd();
        }

        if (option_ == ZMQ.ZMQ_EVENTS) {
            boolean rc = process_commands (0, false);
            if (!rc && errno.get() == ZError.ETERM)
                return -1;
            assert (rc);
            int val = 0;
            if (has_out ())
                val |= ZMQ.ZMQ_POLLOUT;
            if (has_in ())
                val |= ZMQ.ZMQ_POLLIN;
            return val;
        }
        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        return options.getsockopt (option_);
    }

    public boolean bind(final String addr)
    {
        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  Process pending commands, if any.
        boolean brc = process_commands(0, false);
        if (!brc)
            return false;

        //  Parse addr_ string.
        URI uri;
        try {
            uri = new URI(addr);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        String protocol = uri.getScheme();
        String address = uri.getAuthority();
        String path = uri.getPath();
        if (address == null)
            address = path;

        check_protocol(protocol);

        if (protocol.equals("inproc")) {
            Ctx.Endpoint endpoint = new Ctx.Endpoint(this, options);
            boolean rc = register_endpoint(addr, endpoint);
            if (rc) {
                // Save last endpoint URI
                options.last_endpoint = addr;
                errno.set(ZError.EADDRINUSE);
            }
            return rc;
        }
        if (protocol.equals("pgm") || protocol.equals("epgm")) {
            //  For convenience's sake, bind can be used interchageable with
            //  connect for PGM and EPGM transports.
            return connect(addr);
        }

        //  Remaining trasnports require to be run in an I/O thread, so at this
        //  point we'll choose one.
        IOThread io_thread = choose_io_thread(options.affinity);
        if (io_thread == null) {
            throw new IllegalStateException("EMTHREAD");
        }

        if (protocol.equals("tcp")) {
            TcpListener listener = new TcpListener(io_thread, this, options);
            int rc = listener.set_address(address);
            if (rc != 0) {
                listener.destroy();
                event_bind_failed(address, rc);
                errno.set(rc);
                return false;
            }

            // Save last endpoint URI
            options.last_endpoint = listener.get_address();

            add_endpoint(options.last_endpoint, listener);
            return true;
        }

        if (protocol.equals("ipc")) {
            IpcListener listener = new IpcListener(io_thread, this, options);
            int rc = listener.set_address(address);
            if (rc != 0) {
                listener.destroy();
                event_bind_failed(address, rc);
                errno.set(rc);
                return false;
            }

            // Save last endpoint URI
            options.last_endpoint = listener.get_address();

            add_endpoint(addr, listener);
            return true;
        }

        throw new IllegalArgumentException(addr);
    }

    public boolean connect (String addr_)
    {
        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
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
        String address = uri.getAuthority();
        String path = uri.getPath();
        if (address == null)
            address = path;

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
            int  sndhwm = 0;
            if (options.sndhwm != 0 && peer.options.rcvhwm != 0)
                sndhwm = options.sndhwm + peer.options.rcvhwm;
            int  rcvhwm = 0;
            if (options.rcvhwm != 0 && peer.options.sndhwm != 0)
                rcvhwm = options.rcvhwm + peer.options.sndhwm;

            //  Create a bi-directional pipe to connect the peers.
            ZObject[] parents = {this, peer.socket};
            Pipe[] pipes = {null, null};
            int[] hwms = {sndhwm, rcvhwm};
            boolean[] delays = {options.delay_on_disconnect, options.delay_on_close};
            Pipe.pipepair (parents, pipes, hwms, delays);

            //  Attach local end of the pipe to this socket object.
            attach_pipe (pipes [0]);

            //  If required, send the identity of the peer to the local socket.
            if (peer.options.recv_identity) {
                Msg id = new Msg (options.identity_size);
                id.put (options.identity, 0 , options.identity_size);
                id.setFlags (Msg.IDENTITY);
                boolean written = pipes [0].write (id);
                assert (written);
                pipes [0].flush ();
            }

            //  If required, send the identity of the local socket to the peer.
            if (options.recv_identity) {
                Msg id = new Msg (peer.options.identity_size);
                id.put (peer.options.identity, 0 , peer.options.identity_size);
                id.setFlags (Msg.IDENTITY);
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

            // remember inproc connections for disconnect
            inprocs.put(addr_, pipes[0]);

            return true;
        }

        //  Choose the I/O thread to run the session in.
        IOThread io_thread = choose_io_thread (options.affinity);
        if (io_thread == null) {
            throw new IllegalStateException("Empty IO Thread");
        }
        boolean ipv4only = options.ipv4only != 0 ? true : false;
        Address paddr = new Address (protocol, address, ipv4only);

        //  Resolve address (if needed by the protocol)
        paddr.resolve();

        //  Create session.
        SessionBase session = SessionBase.create (io_thread, true, this,
            options, paddr);
        assert (session != null);

        //  PGM does not support subscription forwarding; ask for all data to be
        //  sent to this pipe.
        boolean icanhasall = false;
        if (protocol.equals("pgm") || protocol.equals("epgm"))
            icanhasall = true;

        if (options.delay_attach_on_connect != 1 || icanhasall) {
            //  Create a bi-directional pipe.
            ZObject[] parents = {this, session};
            Pipe[] pipes = {null, null};
            int[] hwms = {options.sndhwm, options.rcvhwm};
            boolean[] delays = {options.delay_on_disconnect, options.delay_on_close};
            Pipe.pipepair (parents, pipes, hwms, delays);

            //  Attach local end of the pipe to the socket object.
            attach_pipe (pipes [0], icanhasall);

            //  Attach remote end of the pipe to the session object later on.
            session.attach_pipe (pipes [1]);
        }

        // Save last endpoint URI
        options.last_endpoint = paddr.toString ();

        add_endpoint (addr_, session);
        return true;
    }


    //  Creates new endpoint ID and adds the endpoint to the map.
    private void add_endpoint (String addr_, Own endpoint_)
    {
        //  Activate the session. Make it a child of this socket.
        launch_child (endpoint_);
        endpoints.put (addr_, endpoint_);
    }

    public boolean term_endpoint(String addr_) {

        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  Check whether endpoint address passed to the function is valid.
        if (addr_ == null) {
            throw new IllegalArgumentException();
        }

        //  Process pending commands, if any, since there could be pending unprocessed process_own()'s
        //  (from launch_child() for example) we're asked to terminate now.
        boolean rc = process_commands (0, false);
        if (!rc)
            return rc;

        //  Parse addr_ string.
        URI uri;
        try {
            uri = new URI(addr_);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        String protocol = uri.getScheme();
        // Disconnect an inproc socket
        if (protocol.equals("inproc")) {
            if (!inprocs.containsKey(addr_)) {
                return false;
            }

            Iterator<Entry<String, Pipe>> it = inprocs.entrySet().iterator();
            while(it.hasNext()) {
                it.next().getValue().terminate(true);
                it.remove();
            }
            return true;
        }

        if (!endpoints.containsKey(addr_)) {
            return false;
        }
        //  Find the endpoints range (if any) corresponding to the addr_ string.
        Iterator<Entry<String, Own>> it = endpoints.entrySet().iterator();

        while(it.hasNext()) {
            Entry<String, Own> e = it.next();
            if (!e.getKey().equals(addr_)) continue;
            term_child(e.getValue());
            it.remove();
        }
        return true;

    }

    public boolean send (Msg msg_, int flags_)
    {
        if (ctx_terminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Check whether message passed to the function is valid.
        if (msg_ == null) {
            throw new IllegalArgumentException();
        }

        //  Process pending commands, if any.
        boolean brc = process_commands (0, true);
        if (!brc)
            return false;

        //  Clear any user-visible flags that are set on the message.
        msg_.resetFlags (Msg.MORE);

        //  At this point we impose the flags on the message.
        if ((flags_ & ZMQ.ZMQ_SNDMORE) > 0)
            msg_.setFlags (Msg.MORE);

        //  Try to send the message.
        boolean rc = xsend(msg_);

        if (rc)
            return true;

        if (errno.get() != ZError.EAGAIN)
            return false;

        //  In case of non-blocking send we'll simply propagate
        //  the error - including EAGAIN - up the stack.
        if ((flags_ & ZMQ.ZMQ_DONTWAIT) > 0 || options.sndtimeo == 0)
            return false;

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care.
        int timeout = options.sndtimeo;
        long end = timeout < 0 ? 0 : (Clock.now_ms () + timeout);

        //  Oops, we couldn't send the message. Wait for the next
        //  command, process it and try to send the message again.
        //  If timeout is reached in the meantime, return EAGAIN.
        while (true) {
            if (!process_commands (timeout, false) )
                return false;

            rc = xsend (msg_);
            if (rc)
                break;

            if (errno.get() != ZError.EAGAIN)
                return false;

            if (timeout > 0) {
                timeout = (int) (end - Clock.now_ms ());
                if (timeout <= 0) {
                    errno.set(ZError.EAGAIN);
                    return false;
                }
            }
        }
        return true;
    }


    public Msg recv(int flags_) {

        if (ctx_terminated) {
            errno.set(ZError.ETERM);
            return null;
        }

        //  Once every inbound_poll_rate messages check for signals and process
        //  incoming commands. This happens only if we are not polling altogether
        //  because there are messages available all the time. If poll occurs,
        //  ticks is set to zero and thus we avoid this code.
        //
        //  Note that 'recv' uses different command throttling algorithm (the one
        //  described above) from the one used by 'send'. This is because counting
        //  ticks is more efficient than doing RDTSC all the time.
        if (++ticks == Config.INBOUND_POLL_RATE.getValue()) {
            if (!process_commands (0, false))
                return null;
            ticks = 0;
        }

        //  Get the message.
        Msg msg_ = xrecv ();
        if (msg_ == null && errno.get() != ZError.EAGAIN)
            return null;

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

            msg_ = xrecv();
            if (msg_ == null)
                return null;
            extract_flags (msg_);
            return msg_;
        }

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care.
        int timeout = options.rcvtimeo;
        long end = timeout < 0 ? 0 : (Clock.now_ms () + timeout);

        //  In blocking scenario, commands are processed over and over again until
        //  we are able to fetch a message.
        boolean block = (ticks != 0);
        while (true) {
            if (!process_commands (block ? timeout : 0, false)) {
                return null;
            }
            msg_ = xrecv();

            if (msg_ != null) {
                ticks = 0;
                break;
            }

            if (errno.get() != ZError.EAGAIN)
                return null;

            block = true;
            if (timeout > 0) {
                timeout = (int) (end - Clock.now_ms ());
                if (timeout <= 0) {
                    errno.set(ZError.EAGAIN);
                    return null;
                }
            }
        }

        extract_flags (msg_);
        return msg_;

    }


    public void close() {
        //  Mark the socket as dead
        tag = 0xdeadbeef;

        //  Transfer the ownership of the socket from this application thread
        //  to the reaper thread which will take care of the rest of shutdown
        //  process.
        send_reap (this);

    }


    //  These functions are used by the polling mechanism to determine
    //  which events are to be reported from this socket.
    public boolean has_in() {
        return xhas_in();
    }

    public boolean has_out() {
        return xhas_out ();
    }


    //  Using this function reaper thread ask the socket to register with
    //  its poller.
    public void start_reaping(Poller poller_) {

        //  Plug the socket to the reaper thread.
        poller = poller_;
        handle = mailbox.get_fd();
        poller.add_fd (handle, this);
        poller.set_pollin (handle);

        //  Initialise the termination and check whether it can be deallocated
        //  immediately.
        terminate ();
        check_destroy ();
    }

    //  Processes commands sent to this socket (if any). If timeout is -1,
    //  returns only after at least one command was processed.
    //  If throttle argument is true, commands are processed at most once
    //  in a predefined time period.
    private boolean process_commands (int timeout_, boolean throttle_)
    {
        Command cmd;
        boolean ret = true;
        if (timeout_ != 0) {

            //  If we are asked to wait, simply ask mailbox to wait.
            cmd = mailbox.recv (timeout_);
        }
        else {

            //  If we are asked not to wait, check whether we haven't processed
            //  commands recently, so that we can throttle the new commands.

            //  Get the CPU's tick counter. If 0, the counter is not available.
            long tsc = 0 ; // save cpu Clock.rdtsc ();

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
                if (tsc >= last_tsc && tsc - last_tsc <= Config.MAX_COMMAND_DELAY.getValue())
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
            errno.set(ZError.ETERM); // Do not raise exception at the blocked operation
            return false;
        }

        return ret;
    }


    @Override
    protected void process_stop ()
    {
        //  Here, someone have called zmq_term while the socket was still alive.
        //  We'll remember the fact so that any blocking call is interrupted and any
        //  further attempt to use the socket will return ETERM. The user is still
        //  responsible for calling zmq_close on the socket though!
        stop_monitor ();
        ctx_terminated = true;

    }

    @Override
    protected void process_bind (Pipe pipe_)
    {
        attach_pipe(pipe_);
    }

    @Override
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

    //  Delay actual destruction of the socket.
    @Override
    protected void process_destroy ()
    {
        destroyed = true;
    }

    //  The default implementation assumes there are no specific socket
    //  options for the particular socket type. If not so, overload this
    //  method.
    protected boolean xsetsockopt(int option_, Object optval_) {
        return false;
    }


    protected boolean xhas_out() {
        return false;
    }

    protected boolean xsend(Msg msg_) {
        throw new UnsupportedOperationException("Must Override");
    }

    protected boolean xhas_in() {
        return false;
    }

    protected Msg xrecv() {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xread_activated(Pipe pipe_) {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xwrite_activated(Pipe pipe_) {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xhiccuped(Pipe pipe_) {
        throw new UnsupportedOperationException("Must override");
    }


    @Override
    public void in_event() {
        //  This function is invoked only once the socket is running in the context
        //  of the reaper thread. Process any commands from other threads/sockets
        //  that may be available at the moment. Ultimately, the socket will
        //  be destroyed.
        try {
            process_commands (0, false);
        } catch (ZError.CtxTerminatedException e) {}

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


    //  To be called after processing commands or invoking any command
    //  handlers explicitly. If required, it will deallocate the socket.
    private void check_destroy ()
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

    @Override
    public void read_activated (Pipe pipe_)
    {
        xread_activated(pipe_);
    }

    @Override
    public void write_activated (Pipe pipe_)
    {
        xwrite_activated (pipe_);
    }

    @Override
    public void hiccuped (Pipe pipe_)
    {
        if (options.delay_attach_on_connect == 1)
            pipe_.terminate (false);
        else
            // Notify derived sockets of the hiccup
            xhiccuped(pipe_);
    }


    @Override
    public void terminated(Pipe pipe_) {
        //  Notify the specific socket type about the pipe termination.
        xterminated (pipe_);

        // Remove pipe from inproc pipes
        Iterator<Entry<String, Pipe>> it = inprocs.entrySet().iterator();
        while(it.hasNext()) {
            if (it.next().getValue() == pipe_) {
                it.remove();
                break;
            }
        }

        //  Remove the pipe from the list of attached pipes and confirm its
        //  termination if we are already shutting down.
        pipes.remove(pipe_);
        if (is_terminating ())
            unregister_term_ack ();

    }



    //  Moves the flags from the message to local variables,
    //  to be later retrieved by getsockopt.
    private void extract_flags(Msg msg_) {
        //  Test whether IDENTITY flag is valid for this socket type.
        if ((msg_.flags () & Msg.IDENTITY) > 0)
            assert (options.recv_identity);

        //  Remove MORE flag.
        rcvmore = msg_.hasMore();
    }


    public boolean monitor (final String addr_, int events_) {
        boolean rc;
        if (ctx_terminated) {
            throw new ZError.CtxTerminatedException();
        }

        // Support deregistering monitoring endpoints as well
        if (addr_ == null) {
            stop_monitor ();
            return true;
        }

        //  Parse addr_ string.
        URI uri;
        try {
            uri = new URI(addr_);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException (e);
        }
        String protocol = uri.getScheme();
        String address = uri.getAuthority();
        String path = uri.getPath();
        if (address == null)
            address = path;

        check_protocol (protocol);

        // Event notification only supported over inproc://
        if (!protocol.equals ("inproc")) {
            stop_monitor ();
            throw new IllegalArgumentException("inproc socket required");
        }

        // Register events to monitor
        monitor_events = events_;

        monitor_socket = get_ctx ().create_socket(ZMQ.ZMQ_PAIR);
        if (monitor_socket == null)
            return false;

        // Never block context termination on pending event messages
        int linger = 0;
        try {
            monitor_socket.setsockopt (ZMQ.ZMQ_LINGER, linger);
        } catch (IllegalArgumentException e) {
            stop_monitor ();
            throw e;
        }

        // Spawn the monitor socket endpoint
        rc = monitor_socket.bind (addr_);
        if (!rc)
             stop_monitor ();
        return rc;
    }

    public void event_connected (String addr, SelectableChannel ch)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_CONNECTED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_CONNECTED, addr, ch));
    }

    public void event_connect_delayed (String addr, int errno)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_CONNECT_DELAYED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_CONNECT_DELAYED, addr, errno));
    }

    public void event_connect_retried (String addr, int interval)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_CONNECT_RETRIED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_CONNECT_RETRIED, addr, interval));
    }

    public void event_listening (String addr, SelectableChannel ch)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_LISTENING) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_LISTENING, addr, ch));
    }

    public void event_bind_failed (String addr, int errno)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_BIND_FAILED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_BIND_FAILED, addr, errno));
    }

    public void event_accepted (String addr, SelectableChannel ch)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_ACCEPTED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_ACCEPTED, addr, ch));
    }

    public void event_accept_failed (String addr, int errno)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_ACCEPT_FAILED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_ACCEPT_FAILED, addr, errno));
    }

    public void event_closed (String addr, SelectableChannel ch)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_CLOSED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_CLOSED, addr, ch));
    }

    public void event_close_failed (String addr, int errno)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_CLOSE_FAILED) == 0)
            return;

        monitor_event (new ZMQ.Event (ZMQ.ZMQ_EVENT_CLOSE_FAILED, addr, errno));
    }

    public void event_disconnected (String addr, SelectableChannel ch)
    {
        if ((monitor_events & ZMQ.ZMQ_EVENT_DISCONNECTED) == 0)
            return;

        monitor_event(new ZMQ.Event(ZMQ.ZMQ_EVENT_DISCONNECTED, addr, ch));
    }

    protected void monitor_event (ZMQ.Event event)
    {

        if (monitor_socket == null)
            return;

        event.write (monitor_socket);
    }

    protected void stop_monitor ()
    {

        if (monitor_socket != null) {
            if ((monitor_events & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) != 0)
                monitor_event(new ZMQ.Event(ZMQ.ZMQ_EVENT_MONITOR_STOPPED, "", 0));
            monitor_socket.close();
            monitor_socket = null;
            monitor_events = 0;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + options.socket_id + "]";
    }

    public SelectableChannel get_fd() {
        return mailbox.get_fd();
    }

    public String typeString() {
        switch (options.type) {
        case ZMQ.ZMQ_PAIR:
            return "PAIR";
        case ZMQ.ZMQ_PUB:
            return "PUB";
        case ZMQ.ZMQ_SUB:
            return "SUB";
        case ZMQ.ZMQ_REQ:
            return "REQ";
        case ZMQ.ZMQ_REP:
            return "REP";
        case ZMQ.ZMQ_DEALER:
            return "DEALER";
        case ZMQ.ZMQ_ROUTER:
            return "ROUTER";
        case ZMQ.ZMQ_PULL:
            return "PULL";
        case ZMQ.ZMQ_PUSH:
            return "PUSH";
        default:
            return "UNKOWN";
        }
    }

    public int errno()
    {
        return errno.get();
    }
}
