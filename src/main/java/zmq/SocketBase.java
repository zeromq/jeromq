package zmq;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.NetProtocol;
import zmq.io.net.ipc.IpcListener;
import zmq.io.net.tcp.TcpAddress;
import zmq.io.net.tcp.TcpListener;
import zmq.io.net.tipc.TipcListener;
import zmq.pipe.Pipe;
import zmq.poll.IPollEvents;
import zmq.poll.Poller;
import zmq.socket.Sockets;
import zmq.util.Blob;
import zmq.util.Clock;
import zmq.util.MultiMap;

public abstract class SocketBase extends Own implements IPollEvents, Pipe.IPipeEvents
{
    private static class EndpointPipe
    {
        private final Own  endpoint;
        private final Pipe pipe;

        public EndpointPipe(Own endpoint, Pipe pipe)
        {
            super();
            this.endpoint = endpoint;
            this.pipe = pipe;
        }

        @Override
        public String toString()
        {
            return "EndpointPipe [endpoint=" + endpoint + ", pipe=" + pipe + "]";
        }
    }

    //  Map of open endpoints.
    private final MultiMap<String, EndpointPipe> endpoints;

    //  Map of open inproc endpoints.
    private final MultiMap<String, Pipe> inprocs;

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

    //  the attached pipes.
    private final Set<Pipe> pipes;

    //  Reaper's poller and handle of this socket within it.
    private Poller        poller;
    private Poller.Handle handle;

    //  Timestamp of when commands were processed the last time.
    private long lastTsc;

    //  Number of messages received since last command processing.
    private int ticks;

    //  True if the last message received had MORE flag set.
    private boolean rcvmore;

    // File descriptor if applicable
    private SocketChannel fileDesc;

    // Monitor socket
    private SocketBase monitorSocket;

    // Bitmask of events being monitored
    private int monitorEvents;

    // Next assigned name on a zmq_connect() call used by ROUTER and STREAM socket types
    protected String connectRid;

    private final ReentrantLock monitorSync = new ReentrantLock(false);

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
        options.ipv6 = parent.get(ZMQ.ZMQ_IPV6) != 0;
        options.linger = parent.get(ZMQ.ZMQ_BLOCKY) != 0 ? -1 : 0;

        endpoints = new MultiMap<>();
        inprocs = new MultiMap<>();
        pipes = new HashSet<>();

        mailbox = new Mailbox(parent, "socket-" + sid, tid);
    }

    //  Concrete algorithms for the x- methods are to be defined by
    //  individual socket types.
    protected abstract void xattachPipe(Pipe pipe, boolean subscribe2all);

    protected abstract void xpipeTerminated(Pipe pipe);

    //  Returns false if object is not a socket.
    final boolean checkTag()
    {
        return tag == 0xbaddecaf;
    }

    @Override
    protected void destroy()
    {
        try {
            monitorSync.lock();
            try {
                mailbox.close();
            }
            catch (IOException ignore) {
            }

            stopMonitor();
            assert (destroyed);
        }
        finally {
            monitorSync.unlock();
        }
    }

    //  Returns the mailbox associated with this socket.
    final Mailbox getMailbox()
    {
        return mailbox;
    }

    //  Interrupt blocking call if the socket is stuck in one.
    //  This function can be called from a different thread!
    final void stop()
    {
        //  Called by ctx when it is terminated (zmq_term).
        //  'stop' command is sent from the threads that called zmq_term to
        //  the thread owning the socket. This way, blocking call in the
        //  owner thread can be interrupted.
        sendStop();
    }

    //  Check whether transport protocol, as specified in connect or
    //  bind, is available and compatible with the socket type.
    private NetProtocol checkProtocol(String protocol)
    {
        //  First check out whether the protcol is something we are aware of.
        NetProtocol proto = NetProtocol.getProtocol(protocol);
        if (proto == null || !proto.valid) {
            errno.set(ZError.EPROTONOSUPPORT);
            return proto;
        }

        //  Check whether socket type and transport protocol match.
        //  Specifically, multicast protocols can't be combined with
        //  bi-directional messaging patterns (socket types).
        if (!proto.compatible(options.type)) {
            errno.set(ZError.ENOCOMPATPROTO);
            return null;
        }
        //  Protocol is available.
        return proto;
    }

    //  Register the pipe with this socket.
    private void attachPipe(Pipe pipe)
    {
        attachPipe(pipe, false);
    }

    private void attachPipe(Pipe pipe, boolean subscribe2all)
    {
        assert (pipe != null);

        //  First, register the pipe so that we can terminate it later on.
        pipe.setEventSink(this);
        pipes.add(pipe);

        //  Let the derived socket type know about new pipe.
        xattachPipe(pipe, subscribe2all);

        //  If the socket is already being closed, ask any new pipes to terminate
        //  straight away.
        if (isTerminating()) {
            registerTermAcks(1);
            pipe.terminate(false);
        }
    }

    public final boolean setSocketOpt(int option, Object optval)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  First, check whether specific socket type overloads the option.
        boolean rc = xsetsockopt(option, optval);
        if (rc || errno.get() != ZError.EINVAL) {
            return rc;
        }

        //  If the socket type doesn't support the option, pass it to
        //  the generic option parser.
        rc = options.setSocketOpt(option, optval);
        if (rc) {
            errno.set(0);
        }
        return rc;
    }

    public final int getSocketOpt(int option)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return -1;
        }

        // fast track to avoid boxing
        if (option == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }
        if (option == ZMQ.ZMQ_EVENTS) {
            boolean rc = processCommands(0, false);
            if (!rc && (errno.get() == ZError.ETERM || errno.get() == ZError.EINTR)) {
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
        Object val = options.getSocketOpt(option);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        if (val instanceof Boolean) {
            return (Boolean) val ? 1 : 0;
        }
        throw new IllegalArgumentException(val + " is neither an integer or a boolean for option " + option);
    }

    public final Object getSocketOptx(int option)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return null;
        }

        if (option == ZMQ.ZMQ_RCVMORE) {
            return rcvmore ? 1 : 0;
        }

        if (option == ZMQ.ZMQ_FD) {
            return mailbox.getFd();
        }

        if (option == ZMQ.ZMQ_EVENTS) {
            boolean rc = processCommands(0, false);
            if (!rc && (errno.get() == ZError.ETERM || errno.get() == ZError.EINTR)) {
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
        return options.getSocketOpt(option);
    }

    public final boolean bind(final String addr)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Process pending commands, if any.
        boolean brc = processCommands(0, false);
        if (!brc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocolName = uri.getProtocol();
        String address = uri.getAddress();

        NetProtocol protocol = checkProtocol(protocolName);
        if (protocol == null || !protocol.valid) {
            return false;
        }

        if (NetProtocol.inproc.equals(protocol)) {
            Ctx.Endpoint endpoint = new Ctx.Endpoint(this, options);
            boolean rc = registerEndpoint(addr, endpoint);
            if (rc) {
                connectPending(addr, this);
                // Save last endpoint URI
                options.lastEndpoint = addr;
            }
            else {
                errno.set(ZError.EADDRINUSE);
            }
            return rc;
        }

        if (NetProtocol.pgm.equals(protocol) || NetProtocol.epgm.equals(protocol)
                || NetProtocol.norm.equals(protocol)) {
            //  For convenience's sake, bind can be used interchangeable with
            //  connect for PGM, EPGM and NORM transports.
            return connect(addr);
        }

        //  Remaining transports require to be run in an I/O thread, so at this
        //  point we'll choose one.
        IOThread ioThread = chooseIoThread(options.affinity);
        if (ioThread == null) {
            errno.set(ZError.EMTHREAD);
            return false;
        }

        if (NetProtocol.tcp.equals(protocol)) {
            TcpListener listener = new TcpListener(ioThread, this, options);
            boolean rc = listener.setAddress(address);
            if (!rc) {
                listener.destroy();
                eventBindFailed(address, errno.get());
                return false;
            }

            // Save last endpoint URI
            options.lastEndpoint = listener.getAddress();

            addEndpoint(addr, listener, null);
            return true;
        }

        if (NetProtocol.ipc.equals(protocol)) {
            IpcListener listener = new IpcListener(ioThread, this, options);
            boolean rc = listener.setAddress(address);
            if (!rc) {
                listener.destroy();
                eventBindFailed(address, errno.get());
                return false;
            }

            // Save last endpoint URI
            options.lastEndpoint = listener.getAddress();

            addEndpoint(addr, listener, null);
            return true;
        }

        if (NetProtocol.tipc.equals(protocol)) {
            TipcListener listener = new TipcListener(ioThread, this, options);
            boolean rc = listener.setAddress(address);
            if (!rc) {
                listener.destroy();
                eventBindFailed(address, errno.get());
                return false;
            }

            // Save last endpoint URI
            options.lastEndpoint = listener.getAddress();

            addEndpoint(addr, listener, null);
            return true;
        }

        throw new IllegalArgumentException(addr);
    }

    public final boolean connect(String addr)
    {
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Process pending commands, if any.
        boolean brc = processCommands(0, false);
        if (!brc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        String protocolName = uri.getProtocol();
        String address = uri.getAddress();

        NetProtocol protocol = checkProtocol(protocolName);
        if (protocol == null || !protocol.valid) {
            return false;
        }

        if (NetProtocol.inproc.equals(protocol)) {
            //  TODO: inproc connect is specific with respect to creating pipes
            //  as there's no 'reconnect' functionality implemented. Once that
            //  is in place we should follow generic pipe creation algorithm.

            //  Find the peer endpoint.
            Ctx.Endpoint peer = findEndpoint(addr);
            // The total HWM for an inproc connection should be the sum of
            // the binder's HWM and the connector's HWM.
            int sndhwm = 0;
            if (peer.socket == null) {
                sndhwm = options.sendHwm;
            }
            else if (options.sendHwm != 0 && peer.options.recvHwm != 0) {
                sndhwm = options.sendHwm + peer.options.recvHwm;
            }
            int rcvhwm = 0;
            if (peer.socket == null) {
                rcvhwm = options.recvHwm;
            }
            else if (options.recvHwm != 0 && peer.options.sendHwm != 0) {
                rcvhwm = options.recvHwm + peer.options.sendHwm;
            }

            //  Create a bi-directional pipe to connect the peers.
            ZObject[] parents = { this, peer.socket == null ? this : peer.socket };

            boolean conflate = options.conflate && (options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_PULL
                    || options.type == ZMQ.ZMQ_PUSH || options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_SUB);

            int[] hwms = { conflate ? -1 : sndhwm, conflate ? -1 : rcvhwm };
            boolean[] conflates = { conflate, conflate };
            Pipe[] pipes = Pipe.pair(parents, hwms, conflates);

            //  Attach local end of the pipe to this socket object.
            attachPipe(pipes[0]);

            if (peer.socket == null) {
                //  The peer doesn't exist yet so we don't know whether
                //  to send the identity message or not. To resolve this,
                //  we always send our identity and drop it later if
                //  the peer doesn't expect it.
                Msg id = new Msg(options.identitySize);
                id.put(options.identity, 0, options.identitySize);
                id.setFlags(Msg.IDENTITY);
                boolean written = pipes[0].write(id);
                assert (written);
                pipes[0].flush();

                pendConnection(addr, new Ctx.Endpoint(this, options), pipes);
            }
            else {
                //  If required, send the identity of the peer to the local socket.
                if (peer.options.recvIdentity) {
                    Msg id = new Msg(options.identitySize);
                    id.put(options.identity, 0, options.identitySize);
                    id.setFlags(Msg.IDENTITY);
                    boolean written = pipes[0].write(id);
                    assert (written);
                    pipes[0].flush();
                }

                //  If required, send the identity of the local socket to the peer.
                if (options.recvIdentity) {
                    Msg id = new Msg(peer.options.identitySize);
                    id.put(peer.options.identity, 0, peer.options.identitySize);
                    id.setFlags(Msg.IDENTITY);
                    boolean written = pipes[1].write(id);
                    assert (written);
                    pipes[1].flush();
                }

                //  Attach remote end of the pipe to the peer socket. Note that peer's
                //  seqnum was incremented in findEndpoint function. We don't need it
                //  increased here.
                sendBind(peer.socket, pipes[1], false);
            }

            // Save last endpoint URI
            options.lastEndpoint = addr;

            // remember inproc connections for disconnect
            inprocs.insert(addr, pipes[0]);

            return true;
        }

        boolean isSingleConnect = options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_SUB
                || options.type == ZMQ.ZMQ_REQ;

        if (isSingleConnect) {
            if (endpoints.hasValues(addr)) {
                // There is no valid use for multiple connects for SUB-PUB nor
                // DEALER-ROUTER nor REQ-REP. Multiple connects produces
                // nonsensical results.
                return true;
            }
        }

        //  Choose the I/O thread to run the session in.
        IOThread ioThread = chooseIoThread(options.affinity);
        if (ioThread == null) {
            errno.set(ZError.EMTHREAD);
            return false;
        }
        Address paddr = new Address(protocolName, address);

        //  Resolve address (if needed by the protocol)
        if (NetProtocol.tcp.equals(protocol) || NetProtocol.ipc.equals(protocol) || NetProtocol.tipc.equals(protocol)) {
            paddr.resolve(options.ipv6);
        }
        // TODO - Should we check address for ZMQ_HAVE_NORM???

        if (NetProtocol.pgm.equals(protocol) || NetProtocol.epgm.equals(protocol)) {
            // TODO V4 init address for pgm & epgm
        }

        //  Create session.
        SessionBase session = Sockets.createSession(ioThread, true, this, options, paddr);
        assert (session != null);

        //  PGM does not support subscription forwarding; ask for all data to be
        //  sent to this pipe. (same for NORM, currently?)
        boolean subscribe2all = NetProtocol.pgm.equals(protocol) || NetProtocol.epgm.equals(protocol)
                || NetProtocol.norm.equals(protocol);

        Pipe newpipe = null;

        if (options.immediate || subscribe2all) {
            //  Create a bi-directional pipe.
            ZObject[] parents = { this, session };
            boolean conflate = options.conflate && (options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_PULL
                    || options.type == ZMQ.ZMQ_PUSH || options.type == ZMQ.ZMQ_PUB || options.type == ZMQ.ZMQ_SUB);

            int[] hwms = { conflate ? -1 : options.sendHwm, conflate ? -1 : options.recvHwm };
            boolean[] conflates = { conflate, conflate };
            Pipe[] pipes = Pipe.pair(parents, hwms, conflates);

            //  Attach local end of the pipe to the socket object.
            attachPipe(pipes[0], subscribe2all);
            newpipe = pipes[0];
            //  Attach remote end of the pipe to the session object later on.
            session.attachPipe(pipes[1]);
        }

        // Save last endpoint URI
        options.lastEndpoint = paddr.toString();

        addEndpoint(addr, session, newpipe);
        return true;
    }

    //  Creates new endpoint ID and adds the endpoint to the map.
    private void addEndpoint(String addr, Own endpoint, Pipe pipe)
    {
        //  Activate the session. Make it a child of this socket.
        launchChild(endpoint);
        endpoints.insert(addr, new EndpointPipe(endpoint, pipe));
    }

    public final boolean termEndpoint(String addr)
    {
        //  Check whether the library haven't been shut down yet.
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Check whether endpoint address passed to the function is valid.
        if (addr == null) {
            errno.set(ZError.EINVAL);
            return false;
        }

        //  Process pending commands, if any, since there could be pending unprocessed processOwn()'s
        //  (from launchChild() for example) we're asked to terminate now.
        boolean rc = processCommands(0, false);
        if (!rc) {
            return false;
        }

        SimpleURI uri = SimpleURI.create(addr);
        NetProtocol protocol = checkProtocol(uri.getProtocol());
        if (protocol == null || !protocol.valid) {
            return false;
        }
        // Disconnect an inproc socket
        if (NetProtocol.inproc.equals(protocol)) {
            if (unregisterEndpoint(addr, this)) {
                return true;
            }

            Collection<Pipe> olds = inprocs.remove(addr);
            if (olds == null || olds.isEmpty()) {
                errno.set(ZError.ENOENT);
                return false;
            }
            else {
                for (Pipe old : olds) {
                    old.terminate(true);
                }
            }
            return true;
        }

        String resolvedAddress = addr;

        // The resolved last_endpoint is used as a key in the endpoints map.
        // The address passed by the user might not match in the TCP case due to
        // IPv4-in-IPv6 mapping (EG: tcp://[::ffff:127.0.0.1]:9999), so try to
        // resolve before giving up. Given at this stage we don't know whether a
        // socket is connected or bound, try with both.
        if (NetProtocol.tcp.equals(protocol)) {
            boolean endpoint = endpoints.hasValues(resolvedAddress);
            if (!endpoint) {
                // TODO V4 resolve TCP address when unbinding
                TcpAddress address = new TcpAddress(uri.getAddress(), options.ipv6);
                resolvedAddress = address.address().toString();
                endpoint = endpoints.hasValues(resolvedAddress);
                if (!endpoint) {
                    // no luck, try with local resolution
                    InetSocketAddress socketAddress = address.resolve(uri.getAddress(), options.ipv6, true);
                    resolvedAddress = socketAddress.toString();
                }
            }
        }

        //  Find the endpoints range (if any) corresponding to the addr_ string.
        Collection<EndpointPipe> eps = endpoints.remove(resolvedAddress);

        if (eps == null || eps.isEmpty()) {
            errno.set(ZError.ENOENT);
            return false;
        }
        else {
            //  If we have an associated pipe, terminate it.
            for (EndpointPipe ep : eps) {
                if (ep.pipe != null) {
                    ep.pipe.terminate(true);
                }
                termChild(ep.endpoint);
            }
        }
        return true;

    }

    public final boolean send(Msg msg, int flags)
    {
        //  Check whether the library haven't been shut down yet.
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return false;
        }

        //  Check whether message passed to the function is valid.
        if (msg == null || !msg.check()) {
            errno.set(ZError.EFAULT);
            return false;
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

        msg.resetMetadata();

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
        //  If the timeout is infinite, don't care.
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

    public final Msg recv(int flags)
    {
        //  Check whether the library haven't been shut down yet.
        if (ctxTerminated) {
            errno.set(ZError.ETERM);
            return null;
        }

        //  Check whether message passed to the function is valid.: NOT APPLICABLE

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
            if (fileDesc != null) {
                msg.setFd(fileDesc);
            }
            extractFlags(msg);
            return msg;
        }

        //  If the message cannot be fetched immediately, there are two scenarios.
        //  For non-blocking recv, commands are processed in case there's an
        //  activate_reader command already waiting in a command pipe.
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
        //  If the timeout is infinite, don't care.
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

    public final void close()
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
    final boolean hasIn()
    {
        return xhasIn();
    }

    final boolean hasOut()
    {
        return xhasOut();
    }

    //  Using this function reaper thread ask the socket to register with
    //  its poller.
    final void startReaping(Poller poller)
    {
        //  Plug the socket to the reaper thread.
        this.poller = poller;
        SelectableChannel fd = mailbox.getFd();
        handle = this.poller.addHandle(fd, this);
        this.poller.setPollIn(handle);

        //  Initialize the termination and check whether it can be deallocated
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
            long tsc = 0; // Clock.rdtsc();

            //  Optimized version of command processing - it doesn't have to check
            //  for incoming commands each time. It does so only if certain time
            //  elapsed since last command processing. Command delay varies
            //  depending on CPU speed: It's ~1ms on 3GHz CPU, ~2ms on 1.5GHz CPU
            //  etc. The optimization makes sense only on platforms where getting
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
        while (cmd != null) {
            cmd.process();
            cmd = mailbox.recv(0);
        }

        if (errno.get() == ZError.EINTR) {
            return false;
        }

        assert (errno.get() == ZError.EAGAIN);

        if (ctxTerminated) {
            errno.set(ZError.ETERM); // Do not raise exception at the blocked operation
            return false;
        }

        return true;
    }

    @Override
    protected final void processStop()
    {
        //  Here, someone have called zmq_term while the socket was still alive.
        //  We'll remember the fact so that any blocking call is interrupted and any
        //  further attempt to use the socket will return ETERM. The user is still
        //  responsible for calling zmq_close on the socket though!
        try {
            monitorSync.lock();
            stopMonitor();
            ctxTerminated = true;
        }
        finally {
            monitorSync.unlock();
        }

    }

    @Override
    protected final void processBind(Pipe pipe)
    {
        attachPipe(pipe);
    }

    @Override
    protected final void processTerm(int linger)
    {
        //  Unregister all inproc endpoints associated with this socket.
        //  Doing this we make sure that no new pipes from other sockets (inproc)
        //  will be initiated.
        unregisterEndpoints(this);

        //  Ask all attached pipes to terminate.
        for (Pipe pipe : pipes) {
            pipe.terminate(false);
        }
        registerTermAcks(pipes.size());

        //  Continue the termination process immediately.
        super.processTerm(linger);
    }

    //  Delay actual destruction of the socket.
    @Override
    protected final void processDestroy()
    {
        destroyed = true;
    }

    //  The default implementation assumes there are no specific socket
    //  options for the particular socket type. If not so, overload this
    //  method.
    protected boolean xsetsockopt(int option, Object optval)
    {
        errno.set(ZError.EINVAL);
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

    protected Blob getCredential()
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
    public final void inEvent()
    {
        //  This function is invoked only once the socket is running in the context
        //  of the reaper thread. Process any commands from other threads/sockets
        //  that may be available at the moment. Ultimately, the socket will
        //  be destroyed.
        processCommands(0, false);
        checkDestroy();
    }

    @Override
    public final void outEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void timerEvent(int id)
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
    public final void readActivated(Pipe pipe)
    {
        xreadActivated(pipe);
    }

    @Override
    public final void writeActivated(Pipe pipe)
    {
        xwriteActivated(pipe);
    }

    @Override
    public final void hiccuped(Pipe pipe)
    {
        if (!options.immediate) {
            pipe.terminate(false);
        }
        else {
            // Notify derived sockets of the hiccup
            xhiccuped(pipe);
        }
    }

    @Override
    public final void pipeTerminated(Pipe pipe)
    {
        //  Notify the specific socket type about the pipe termination.
        xpipeTerminated(pipe);

        // Remove pipe from inproc pipes
        inprocs.remove(pipe);

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
        if (msg.isIdentity()) {
            assert (options.recvIdentity);
        }

        //  Remove MORE flag.
        rcvmore = msg.hasMore();
    }

    public final boolean monitor(final String addr, int events)
    {
        try {
            monitorSync.lock();

            boolean rc;
            if (ctxTerminated) {
                errno.set(ZError.ETERM);
                return false;
            }

            // Support deregistering monitoring endpoints as well
            if (addr == null) {
                stopMonitor();
                return true;
            }

            SimpleURI uri = SimpleURI.create(addr);

            NetProtocol protocol = checkProtocol(uri.getProtocol());
            if (protocol == null || !protocol.valid) {
                return false;
            }

            // Event notification only supported over inproc://
            if (!NetProtocol.inproc.equals(protocol)) {
                errno.set(ZError.EPROTONOSUPPORT);
                return false;
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
        finally {
            monitorSync.unlock();
        }

    }

    public final void eventHandshaken(String addr, int zmtpVersion)
    {
        event(addr, zmtpVersion, ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL);
    }

    public final void eventConnected(String addr, SelectableChannel ch)
    {
        event(addr, ch, ZMQ.ZMQ_EVENT_CONNECTED);
    }

    public final void eventConnectDelayed(String addr, int errno)
    {
        event(addr, errno, ZMQ.ZMQ_EVENT_CONNECT_DELAYED);
    }

    public final void eventConnectRetried(String addr, int interval)
    {
        try {
            monitorSync.lock();
            if ((monitorEvents & ZMQ.ZMQ_EVENT_CONNECT_RETRIED) == 0) {
                return;
            }

            monitorEvent(new ZMQ.Event(ZMQ.ZMQ_EVENT_CONNECT_RETRIED, addr, interval));
        }
        finally {
            monitorSync.unlock();
        }
    }

    public final void eventListening(String addr, SelectableChannel ch)
    {
        event(addr, ch, ZMQ.ZMQ_EVENT_LISTENING);
    }

    public final void eventBindFailed(String addr, int errno)
    {
        event(addr, errno, ZMQ.ZMQ_EVENT_BIND_FAILED);
    }

    public final void eventAccepted(String addr, SelectableChannel ch)
    {
        event(addr, ch, ZMQ.ZMQ_EVENT_ACCEPTED);
    }

    public final void eventAcceptFailed(String addr, int errno)
    {
        event(addr, errno, ZMQ.ZMQ_EVENT_ACCEPT_FAILED);
    }

    public final void eventClosed(String addr, SelectableChannel ch)
    {
        event(addr, ch, ZMQ.ZMQ_EVENT_CLOSED);
    }

    public final void eventCloseFailed(String addr, int errno)
    {
        event(addr, errno, ZMQ.ZMQ_EVENT_CLOSE_FAILED);
    }

    public final void eventDisconnected(String addr, SelectableChannel ch)
    {
        event(addr, ch, ZMQ.ZMQ_EVENT_DISCONNECTED);
    }

    private void event(String addr, Object arg, int event)
    {
        try {
            monitorSync.lock();
            if ((monitorEvents & event) == 0) {
                return;
            }

            monitorEvent(new ZMQ.Event(event, addr, arg));
        }
        finally {
            monitorSync.unlock();
        }
    }

    //  Send a monitor event
    protected final void monitorEvent(ZMQ.Event event)
    {
        if (monitorSocket == null) {
            return;
        }

        event.write(monitorSocket);
    }

    private void stopMonitor()
    {
        // this is a private method which is only called from
        // contexts where the mutex has been locked before

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
        return getClass().getSimpleName() + "[" + options.socketId + "]";
    }

    public final SelectableChannel getFD()
    {
        return mailbox.getFd();
    }

    public String typeString()
    {
        return Sockets.name(options.type);
    }

    public final int errno()
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

    @Override
    public final void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }
}
