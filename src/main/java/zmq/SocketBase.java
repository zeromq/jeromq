package zmq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class SocketBase extends Own
    implements IPollEvents, Pipe.IPipeEvents
{
    //  Map of open endpoints.
    private final Map<String, Own> endpoints;

    //  Map of open inproc endpoints.
    private final Map<String, Pipe> inprocs;

    //  Used to check whether the object is a socket.
    private int tag;

    //  If true, associated context was already terminated.
    private boolean ctxTerminated;

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
    private long lastTsc;

    //  Number of messages received since last command processing.
    private int ticks;

    //  True if the last message received had MORE flag set.
    private boolean rcvmore;

    // Monitor socket
    private SocketBase monitorSocket;

    // Bitmask of events being monitored
    private int monitorEvents;

    protected ValueReference<Integer> errno;

    protected SocketBase(Ctx parent, int tid, int sid)
    {
        super(parent, tid);
        tag = 0xbaddecaf;
        ctxTerminated = false;
        destroyed = false;
        lastTsc = 0;
        ticks = 0;
        rcvmore = false;
        monitorSocket = null;
        monitorEvents = 0;

        options.socketId = sid;
        options.linger = parent.get(ZMQ.ZMQ_BLOCKY) != 0 ? -1 : 0;

        endpoints = new MultiMap<String, Own>();
        inprocs = new MultiMap<String, Pipe>();
        pipes = new ArrayList<Pipe>();

        mailbox = new Mailbox("socket-" + sid);

        errno = new ValueReference<Integer>(0);
    }

    //  Concrete algorithms for the x- methods are to be defined by
    //  individual socket types.
    protected abstract void xattachPipe(Pipe pipe, boolean icanhasall);
    protected abstract void xpipeTerminated(Pipe pipe);

    //  Returns false if object is not a socket.
    public boolean checkTag()
    {
        return tag == 0xbaddecaf;
    }

    //  Create a socket of a specified type.
    public static SocketBase create(int type, Ctx parent, int tid, int sid)
    {
        SocketBase s = null;
        switch (type) {
        case ZMQ.ZMQ_PAIR:
            s = new Pair(parent, tid, sid);
            break;
        case ZMQ.ZMQ_PUB:
            s = new Pub(parent, tid, sid);
            break;
        case ZMQ.ZMQ_SUB:
            s = new Sub(parent, tid, sid);
            break;
        case ZMQ.ZMQ_REQ:
            s = new Req(parent, tid, sid);
            break;
        case ZMQ.ZMQ_REP:
            s = new Rep(parent, tid, sid);
            break;
        case ZMQ.ZMQ_DEALER:
            s = new Dealer(parent, tid, sid);
            break;
        case ZMQ.ZMQ_ROUTER:
            s = new Router(parent, tid, sid);
            break;
        case ZMQ.ZMQ_PULL:
            s = new Pull(parent, tid, sid);
            break;
        case ZMQ.ZMQ_PUSH:
            s = new Push(parent, tid, sid);
            break;

        case ZMQ.ZMQ_XPUB:
            s = new XPub(parent, tid, sid);
            break;

        case ZMQ.ZMQ_XSUB:
            s = new XSub(parent, tid, sid);
            break;

        default:
            throw new IllegalArgumentException("type=" + type);
        }
        return s;
    }

    public void destroy()
    {
        try {
            mailbox.close();
        }
        catch (IOException ignore) {
        }

        stopMonitor();
        assert (destroyed);
    }

    //  Returns the mailbox associated with this socket.
    public Mailbox getMailbox()
    {
        return mailbox;
    }

    //  Interrupt blocking call if the socket is stuck in one.
    //  This function can be called from a different thread!
    public void stop()
    {
        //  Called by ctx when it is terminated (zmq_term).
        //  'stop' command is sent from the threads that called zmq_term to
        //  the thread owning the socket. This way, blocking call in the
        //  owner thread can be interrupted.
        sendStop();
    }

    //  Check whether transport protocol, as specified in connect or
    //  bind, is available and compatible with the socket type.
    private void checkProtocol(String protocol)
    {
        //  First check out whether the protcol is something we are aware of.
        if (!protocol.equals("inproc") && !protocol.equals("ipc") && !protocol.equals("tcp") /*&&
              !protocol.equals("pgm") && !protocol.equals("epgm")*/) {
            throw new UnsupportedOperationException(protocol);
        }

        //  Check whether socket type and transport protocol match.
        //  Specifically, multicast protocols can't be combined with
        //  bi-directional messaging patterns (socket types).
        if ((protocol.equals("pgm") || protocol.equals("epgm")) &&
              options.type != ZMQ.ZMQ_PUB && options.type != ZMQ.ZMQ_SUB &&
              options.type != ZMQ.ZMQ_XPUB && options.type != ZMQ.ZMQ_XSUB) {
            throw new UnsupportedOperationException(protocol + ",type=" + options.type);
        }

        //  Protocol is available.
    }

    //  Register the pipe with this socket.
    private void attachPipe(Pipe pipe)
    {
        attachPipe(pipe, false);
    }

    private void attachPipe(Pipe pipe, boolean icanhasall)
    {
        //  First, register the pipe so that we can terminate it later on.

        pipe.setEventSink(this);
        pipes.add(pipe);

        //  Let the derived socket type know about new pipe.
        xattachPipe(pipe, icanhasall);

        //  If the socket is already being closed, ask any new pipes to terminate
        //  straight away.
        if (isTerminating()) {
            registerTermAcks(1);
            pipe.terminate(false);
        }
    }

    public void setSocketOpt(int option, Object optval)
    {
        if (ctxTerminated && option != zmq.ZMQ.ZMQ_LINGER) {
            throw new ZError.CtxTerminatedException();
        }

        //  First, check whether specific socket type overloads the option.
        if (xsetsockopt(option, optval)) {
            return;
        }

        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        options.setSocketOpt(option, optval);
    }

    public int getSocketOpt(int option)
    {
        if (option != ZMQ.ZMQ_EVENTS && ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        // fast track to avoid boxing
        if (option == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }
        if (option == ZMQ.ZMQ_EVENTS) {
            boolean rc = processCommands(0, false);
            if (!rc && errno.get() == ZError.ETERM) {
                return -1;
            }
            assert (rc);
            int val = 0;
            if (hasOut()) {
                val |= ZMQ.ZMQ_POLLOUT;
            }
            if (hasIn()) {
                val |= ZMQ.ZMQ_POLLIN;
            }
            return val;
        }

        return (Integer) getsockoptx(option);
    }

    public Object getsockoptx(int option)
    {
        if (ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        if (option == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }

        if (option == ZMQ.ZMQ_FD) {
            return mailbox.getFd();
        }

        if (option == ZMQ.ZMQ_EVENTS) {
            boolean rc = processCommands(0, false);
            if (!rc && errno.get() == ZError.ETERM) {
                return -1;
            }
            assert (rc);
            int val = 0;
            if (hasOut()) {
                val |= ZMQ.ZMQ_POLLOUT;
            }
            if (hasIn()) {
                val |= ZMQ.ZMQ_POLLIN;
            }
            return val;
        }
        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        return options.getsockopt(option);
    }

    public boolean bind(final String addr)
    {
        if (ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  Process pending commands, if any.
        boolean brc = processCommands(0, false);
        if (!brc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocol = uri.getProtocol();
        String address = uri.getAddress();

        checkProtocol(protocol);

        if (protocol.equals("inproc")) {
            Ctx.Endpoint endpoint = new Ctx.Endpoint(this, options);
            boolean rc = registerEndpoint(addr, endpoint);
            if (rc) {
                // Save last endpoint URI
                options.lastEndpoint = addr;
            }
            else {
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
        IOThread ioThread = chooseIoThread(options.affinity);
        if (ioThread == null) {
            throw new IllegalStateException("EMTHREAD");
        }

        if (protocol.equals("tcp")) {
            TcpListener listener = new TcpListener(ioThread, this, options);
            int rc = listener.setAddress(address);
            if (rc != 0) {
                listener.destroy();
                eventBindFailed(address, rc);
                errno.set(rc);
                return false;
            }

            // Save last endpoint URI
            options.lastEndpoint = listener.getAddress();

            addEndpoint(addr, listener);
            return true;
        }

        if (protocol.equals("ipc")) {
            IpcListener listener = new IpcListener(ioThread, this, options);
            int rc = listener.setAddress(address);
            if (rc != 0) {
                listener.destroy();
                eventBindFailed(address, rc);
                errno.set(rc);
                return false;
            }

            // Save last endpoint URI
            options.lastEndpoint = listener.getAddress();

            addEndpoint(addr, listener);
            return true;
        }

        throw new IllegalArgumentException(addr);
    }

    public boolean connect(String addr)
    {
        if (ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  Process pending commands, if any.
        boolean brc = processCommands(0, false);
        if (!brc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocol = uri.getProtocol();
        String address = uri.getAddress();

        checkProtocol(protocol);

        if (protocol.equals("inproc")) {
            //  TODO: inproc connect is specific with respect to creating pipes
            //  as there's no 'reconnect' functionality implemented. Once that
            //  is in place we should follow generic pipe creation algorithm.

            //  Find the peer endpoint.
            Ctx.Endpoint peer = findEndpoint(addr);
            if (peer.socket == null) {
                return false;
            }
            // The total HWM for an inproc connection should be the sum of
            // the binder's HWM and the connector's HWM.
            int sndhwm = 0;
            if (options.sendHwm != 0 && peer.options.recvHwm != 0) {
                sndhwm = options.sendHwm + peer.options.recvHwm;
            }
            int rcvhwm = 0;
            if (options.recvHwm != 0 && peer.options.sendHwm != 0) {
                rcvhwm = options.recvHwm + peer.options.sendHwm;
            }

            //  Create a bi-directional pipe to connect the peers.
            ZObject[] parents = {this, peer.socket};
            Pipe[] pipes = {null, null};
            int[] hwms = {sndhwm, rcvhwm};
            boolean[] delays = {options.delayOnDisconnect, options.delayOnClose};
            Pipe.pipepair(parents, pipes, hwms, delays);

            //  Attach local end of the pipe to this socket object.
            attachPipe(pipes[0]);

            //  If required, send the identity of the peer to the local socket.
            if (peer.options.recvIdentity) {
                Msg id = new Msg(options.identitySize);
                id.put(options.identity, 0 , options.identitySize);
                id.setFlags(Msg.IDENTITY);
                boolean written = pipes[0].write(id);
                assert (written);
                pipes[0].flush();
            }

            //  If required, send the identity of the local socket to the peer.
            if (options.recvIdentity) {
                Msg id = new Msg(peer.options.identitySize);
                id.put(peer.options.identity, 0 , peer.options.identitySize);
                id.setFlags(Msg.IDENTITY);
                boolean written = pipes[1].write(id);
                assert (written);
                pipes[1].flush();
            }

            //  Attach remote end of the pipe to the peer socket. Note that peer's
            //  seqnum was incremented in findEndpoint function. We don't need it
            //  increased here.
            sendBind(peer.socket, pipes[1], false);

            // Save last endpoint URI
            options.lastEndpoint = addr;

            // remember inproc connections for disconnect
            inprocs.put(addr, pipes[0]);

            return true;
        }

        //  Choose the I/O thread to run the session in.
        IOThread ioThread = chooseIoThread(options.affinity);
        if (ioThread == null) {
            throw new IllegalStateException("Empty IO Thread");
        }
        boolean ipv4only = options.ipv4only != 0;
        Address paddr = new Address(protocol, address, ipv4only);

        //  Resolve address (if needed by the protocol)
        paddr.resolve();

        //  Create session.
        SessionBase session = SessionBase.create(ioThread, true, this,
            options, paddr);
        assert (session != null);

        //  PGM does not support subscription forwarding; ask for all data to be
        //  sent to this pipe.
        boolean icanhasall = false;
        if (protocol.equals("pgm") || protocol.equals("epgm")) {
            icanhasall = true;
        }

        if (options.delayAttachOnConnect != 1 || icanhasall) {
            //  Create a bi-directional pipe.
            ZObject[] parents = {this, session};
            Pipe[] pipes = {null, null};
            int[] hwms = {options.sendHwm, options.recvHwm};
            boolean[] delays = {options.delayOnDisconnect, options.delayOnClose};
            Pipe.pipepair(parents, pipes, hwms, delays);

            //  Attach local end of the pipe to the socket object.
            attachPipe(pipes[0], icanhasall);

            //  Attach remote end of the pipe to the session object later on.
            session.attachPipe(pipes[1]);
        }

        // Save last endpoint URI
        options.lastEndpoint = paddr.toString();

        addEndpoint(addr, session);
        return true;
    }

    //  Creates new endpoint ID and adds the endpoint to the map.
    private void addEndpoint(String addr, Own endpoint)
    {
        //  Activate the session. Make it a child of this socket.
        launchChild(endpoint);
        endpoints.put(addr, endpoint);
    }

    public boolean termEndpoint(String addr)
    {
        if (ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        //  Check whether endpoint address passed to the function is valid.
        if (addr == null) {
            throw new IllegalArgumentException();
        }

        //  Process pending commands, if any, since there could be pending unprocessed processOwn()'s
        //  (from launchChild() for example) we're asked to terminate now.
        boolean rc = processCommands(0, false);
        if (!rc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocol = uri.getProtocol();

        // Disconnect an inproc socket
        if (protocol.equals("inproc")) {
            if (!inprocs.containsKey(addr)) {
                return false;
            }

            Iterator<Entry<String, Pipe>> it = inprocs.entrySet().iterator();
            while (it.hasNext()) {
                it.next().getValue().terminate(true);
                it.remove();
            }
            return true;
        }

        if (!endpoints.containsKey(addr)) {
            return false;
        }
        //  Find the endpoints range (if any) corresponding to the addr_ string.
        Iterator<Entry<String, Own>> it = endpoints.entrySet().iterator();

        while (it.hasNext()) {
            Entry<String, Own> e = it.next();
            if (!e.getKey().equals(addr)) {
                continue;
            }
            termChild(e.getValue());
            it.remove();
        }
        return true;

    }

    public boolean send(Msg msg, int flags)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Check whether message passed to the function is valid.
        if (msg == null) {
            throw new IllegalArgumentException();
        }

        //  Process pending commands, if any.
        boolean brc = processCommands(0, true);
        if (!brc) {
            return false;
        }

        //  Clear any user-visible flags that are set on the message.
        msg.resetFlags(Msg.MORE);

        //  At this point we impose the flags on the message.
        if ((flags & ZMQ.ZMQ_SNDMORE) > 0) {
            msg.setFlags(Msg.MORE);
        }

        //  Try to send the message.
        boolean rc = xsend(msg);

        if (rc) {
            return true;
        }

        if (errno.get() != ZError.EAGAIN) {
            return false;
        }

        //  In case of non-blocking send we'll simply propagate
        //  the error - including EAGAIN - up the stack.
        if ((flags & ZMQ.ZMQ_DONTWAIT) > 0 || options.sendTimeout == 0) {
            return false;
        }

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care.
        int timeout = options.sendTimeout;
        long end = timeout < 0 ? 0 : (Clock.nowMS() + timeout);

        //  Oops, we couldn't send the message. Wait for the next
        //  command, process it and try to send the message again.
        //  If timeout is reached in the meantime, return EAGAIN.
        while (true) {
            if (!processCommands(timeout, false)) {
                return false;
            }

            rc = xsend(msg);
            if (rc) {
                break;
            }

            if (errno.get() != ZError.EAGAIN) {
                return false;
            }

            if (timeout > 0) {
                timeout = (int) (end - Clock.nowMS());
                if (timeout <= 0) {
                    errno.set(ZError.EAGAIN);
                    return false;
                }
            }
        }
        return true;
    }

    public Msg recv(int flags)
    {
        if (ctxTerminated) {
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
            if (!processCommands(0, false)) {
                return null;
            }
            ticks = 0;
        }

        //  Get the message.
        Msg msg = xrecv();
        if (msg == null && errno.get() != ZError.EAGAIN) {
            return null;
        }

        //  If we have the message, return immediately.
        if (msg != null) {
            extractFlags(msg);
            return msg;
        }

        //  If the message cannot be fetched immediately, there are two scenarios.
        //  For non-blocking recv, commands are processed in case there's an
        //  activate_reader command already waiting int a command pipe.
        //  If it's not, return EAGAIN.
        if ((flags & ZMQ.ZMQ_DONTWAIT) > 0 || options.recvTimeout == 0) {
            if (!processCommands(0, false)) {
                return null;
            }
            ticks = 0;

            msg = xrecv();
            if (msg == null) {
                return null;
            }
            extractFlags(msg);
            return msg;
        }

        //  Compute the time when the timeout should occur.
        //  If the timeout is infite, don't care.
        int timeout = options.recvTimeout;
        long end = timeout < 0 ? 0 : (Clock.nowMS() + timeout);

        //  In blocking scenario, commands are processed over and over again until
        //  we are able to fetch a message.
        boolean block = (ticks != 0);
        while (true) {
            if (!processCommands(block ? timeout : 0, false)) {
                return null;
            }
            msg = xrecv();

            if (msg != null) {
                ticks = 0;
                break;
            }

            if (errno.get() != ZError.EAGAIN) {
                return null;
            }

            block = true;
            if (timeout > 0) {
                timeout = (int) (end - Clock.nowMS());
                if (timeout <= 0) {
                    errno.set(ZError.EAGAIN);
                    return null;
                }
            }
        }

        extractFlags(msg);
        return msg;

    }

    public void close()
    {
        //  Mark the socket as dead
        tag = 0xdeadbeef;

        //  Transfer the ownership of the socket from this application thread
        //  to the reaper thread which will take care of the rest of shutdown
        //  process.
        sendReap(this);
    }

    //  These functions are used by the polling mechanism to determine
    //  which events are to be reported from this socket.
    boolean hasIn()
    {
        return xhasIn();
    }

    boolean hasOut()
    {
        return xhasOut();
    }

    //  Using this function reaper thread ask the socket to register with
    //  its poller.
    public void startReaping(Poller poller)
    {
        //  Plug the socket to the reaper thread.
        this.poller = poller;
        handle = mailbox.getFd();
        this.poller.addHandle(handle, this);
        this.poller.setPollIn(handle);

        //  Initialise the termination and check whether it can be deallocated
        //  immediately.
        terminate();
        checkDestroy();
    }

    //  Processes commands sent to this socket (if any). If timeout is -1,
    //  returns only after at least one command was processed.
    //  If throttle argument is true, commands are processed at most once
    //  in a predefined time period.
    private boolean processCommands(int timeout, boolean throttle)
    {
        Command cmd;
        if (timeout != 0) {
            //  If we are asked to wait, simply ask mailbox to wait.
            cmd = mailbox.recv(timeout);
        }
        else {
            //  If we are asked not to wait, check whether we haven't processed
            //  commands recently, so that we can throttle the new commands.

            //  Get the CPU's tick counter. If 0, the counter is not available.
            long tsc = 0; // save cpu Clock.rdtsc ();

            //  Optimised version of command processing - it doesn't have to check
            //  for incoming commands each time. It does so only if certain time
            //  elapsed since last command processing. Command delay varies
            //  depending on CPU speed: It's ~1ms on 3GHz CPU, ~2ms on 1.5GHz CPU
            //  etc. The optimisation makes sense only on platforms where getting
            //  a timestamp is a very cheap operation (tens of nanoseconds).
            if (tsc != 0 && throttle) {
                //  Check whether TSC haven't jumped backwards (in case of migration
                //  between CPU cores) and whether certain time have elapsed since
                //  last command processing. If it didn't do nothing.
                if (tsc >= lastTsc && tsc - lastTsc <= Config.MAX_COMMAND_DELAY.getValue()) {
                    return true;
                }
                lastTsc = tsc;
            }

            //  Check whether there are any commands pending for this thread.
            cmd = mailbox.recv(0);
        }

        //  Process all the commands available at the moment.
        while (true) {
            if (cmd == null) {
                break;
            }

            cmd.destination().processCommand(cmd);
            cmd = mailbox.recv(0);
        }
        if (ctxTerminated) {
            errno.set(ZError.ETERM); // Do not raise exception at the blocked operation
            return false;
        }

        return true;
    }

    @Override
    protected void processStop()
    {
        //  Here, someone have called zmq_term while the socket was still alive.
        //  We'll remember the fact so that any blocking call is interrupted and any
        //  further attempt to use the socket will return ETERM. The user is still
        //  responsible for calling zmq_close on the socket though!
        stopMonitor();
        ctxTerminated = true;

    }

    @Override
    protected void processBind(Pipe pipe)
    {
        attachPipe(pipe);
    }

    @Override
    protected void processTerm(int linger)
    {
        //  Unregister all inproc endpoints associated with this socket.
        //  Doing this we make sure that no new pipes from other sockets (inproc)
        //  will be initiated.
        unregisterEndpoints(this);

        //  Ask all attached pipes to terminate.
        for (int i = 0; i != pipes.size(); ++i) {
            pipes.get(i).terminate(false);
        }
        registerTermAcks(pipes.size());

        //  Continue the termination process immediately.
        super.processTerm(linger);
    }

    //  Delay actual destruction of the socket.
    @Override
    protected void processDestroy()
    {
        destroyed = true;
    }

    //  The default implementation assumes there are no specific socket
    //  options for the particular socket type. If not so, overload this
    //  method.
    protected boolean xsetsockopt(int option, Object optval)
    {
        return false;
    }

    protected boolean xhasOut()
    {
        return false;
    }

    protected boolean xsend(Msg msg)
    {
        throw new UnsupportedOperationException("Must Override");
    }

    protected boolean xhasIn()
    {
        return false;
    }

    protected Msg xrecv()
    {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xreadActivated(Pipe pipe)
    {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xwriteActivated(Pipe pipe)
    {
        throw new UnsupportedOperationException("Must Override");
    }

    protected void xhiccuped(Pipe pipe)
    {
        throw new UnsupportedOperationException("Must override");
    }

    @Override
    public void inEvent()
    {
        //  This function is invoked only once the socket is running in the context
        //  of the reaper thread. Process any commands from other threads/sockets
        //  that may be available at the moment. Ultimately, the socket will
        //  be destroyed.
        try {
            processCommands(0, false);
        }
        catch (ZError.CtxTerminatedException e) {
        }

        checkDestroy();
    }

    @Override
    public void outEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timerEvent(int id)
    {
        throw new UnsupportedOperationException();
    }

    //  To be called after processing commands or invoking any command
    //  handlers explicitly. If required, it will deallocate the socket.
    private void checkDestroy()
    {
        //  If the object was already marked as destroyed, finish the deallocation.
        if (destroyed) {
            //  Remove the socket from the reaper's poller.
            poller.removeHandle(handle);
            //  Remove the socket from the context.
            destroySocket(this);

            //  Notify the reaper about the fact.
            sendReaped();

            //  Deallocate.
            super.processDestroy();
        }
    }

    @Override
    public void readActivated(Pipe pipe)
    {
        xreadActivated(pipe);
    }

    @Override
    public void writeActivated(Pipe pipe)
    {
        xwriteActivated(pipe);
    }

    @Override
    public void hiccuped(Pipe pipe)
    {
        if (options.delayAttachOnConnect == 1) {
            pipe.terminate(false);
        }
        else {
            // Notify derived sockets of the hiccup
            xhiccuped(pipe);
        }
    }

    @Override
    public void pipeTerminated(Pipe pipe)
    {
        //  Notify the specific socket type about the pipe termination.
        xpipeTerminated(pipe);

        // Remove pipe from inproc pipes
        Iterator<Entry<String, Pipe>> it = inprocs.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() == pipe) {
                it.remove();
                break;
            }
        }

        //  Remove the pipe from the list of attached pipes and confirm its
        //  termination if we are already shutting down.
        pipes.remove(pipe);
        if (isTerminating()) {
            unregisterTermAck();
        }
    }

    //  Moves the flags from the message to local variables,
    //  to be later retrieved by getSocketOpt.
    private void extractFlags(Msg msg)
    {
        //  Test whether IDENTITY flag is valid for this socket type.
        if ((msg.flags() & Msg.IDENTITY) > 0) {
            assert (options.recvIdentity);
        }

        //  Remove MORE flag.
        rcvmore = msg.hasMore();
    }

    public boolean monitor(final String addr, int events)
    {
        boolean rc;
        if (ctxTerminated) {
            throw new ZError.CtxTerminatedException();
        }

        // Support deregistering monitoring endpoints as well
        if (addr == null) {
            stopMonitor();
            return true;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocol = uri.getProtocol();

        checkProtocol(protocol);

        // Event notification only supported over inproc://
        if (!protocol.equals("inproc")) {
            stopMonitor();
            throw new IllegalArgumentException("inproc socket required");
        }

        // Register events to monitor
        monitorEvents = events;

        monitorSocket = getCtx().createSocket(ZMQ.ZMQ_PAIR);
        if (monitorSocket == null) {
            return false;
        }

        // Never block context termination on pending event messages
        int linger = 0;
        try {
            monitorSocket.setSocketOpt(ZMQ.ZMQ_LINGER, linger);
        }
        catch (IllegalArgumentException e) {
            stopMonitor();
            throw e;
        }

        // Spawn the monitor socket endpoint
        rc = monitorSocket.bind(addr);
        if (!rc) {
            stopMonitor();
        }
        return rc;
    }

    public void eventConnected(String addr, SelectableChannel ch)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_CONNECTED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CONNECTED, addr, ch));
    }

    public void eventConnectDelayed(String addr, int errno)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_CONNECT_DELAYED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CONNECT_DELAYED, addr, errno));
    }

    public void eventConnectRetried(String addr, int interval)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_CONNECT_RETRIED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CONNECT_RETRIED, addr, interval));
    }

    public void eventListening(String addr, SelectableChannel ch)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_LISTENING) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_LISTENING, addr, ch));
    }

    public void eventBindFailed(String addr, int errno)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_BIND_FAILED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_BIND_FAILED, addr, errno));
    }

    public void eventAccepted(String addr, SelectableChannel ch)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_ACCEPTED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_ACCEPTED, addr, ch));
    }

    public void eventAcceptFailed(String addr, int errno)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_ACCEPT_FAILED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_ACCEPT_FAILED, addr, errno));
    }

    public void eventClosed(String addr, SelectableChannel ch)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_CLOSED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CLOSED, addr, ch));
    }

    public void eventCloseFailed(String addr, int errno)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_CLOSE_FAILED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CLOSE_FAILED, addr, errno));
    }

    public void eventDisconnected(String addr, SelectableChannel ch)
    {
        if ((monitorEvents & ZMQ.ZMQ_EVENT_DISCONNECTED) == 0) {
            return;
        }

        monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_DISCONNECTED, addr, ch));
    }

    protected void monitorEvent(ZMQ.Event event)
    {
        if (monitorSocket == null) {
            return;
        }

        event.write(monitorSocket);
    }

    protected void stopMonitor()
    {
        if (monitorSocket != null) {
            if ((monitorEvents & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) != 0) {
                monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_MONITOR_STOPPED, "", 0));
            }
            monitorSocket.close();
            monitorSocket = null;
            monitorEvents = 0;
        }
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + options.socketId + "]";
    }

    public SelectableChannel getFD()
    {
        return mailbox.getFd();
    }

    public String typeString()
    {
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

    private static class SimpleURI
    {
        private final String protocol;
        private final String address;

        private SimpleURI(String protocol, String address)
        {
            this.protocol = protocol;
            this.address = address;
        }

        public static SimpleURI create(String value)
        {
            int pos = value.indexOf("://");
            if (pos < 0) {
                throw new IllegalArgumentException("Invalid URI: " + value);
            }
            String protocol = value.substring(0, pos);
            String address = value.substring(pos + 3);

            if (protocol.isEmpty() || address.isEmpty()) {
                throw new IllegalArgumentException("Invalid URI: " + value);
            }
            return new SimpleURI(protocol, address);
        }

        public String getProtocol()
        {
            return protocol;
        }

        public String getAddress()
        {
            return address;
        }
    }
}
