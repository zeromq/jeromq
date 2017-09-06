package zmq;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zmq.io.IOThread;
import zmq.pipe.Pipe;
import zmq.socket.Sockets;
import zmq.util.Errno;

//Context object encapsulates all the global state associated with
//  the library.

public class Ctx
{
    private static final int WAIT_FOREVER = -1;

    //  Information associated with inproc endpoint. Note that endpoint options
    //  are registered as well so that the peer can access them without a need
    //  for synchronization, handshaking or similar.

    public static class Endpoint
    {
        public final SocketBase socket;
        public final Options    options;

        public Endpoint(SocketBase socket, Options options)
        {
            this.socket = socket;
            this.options = options;
        }

    }

    private static class PendingConnection
    {
        private final Endpoint endpoint;
        private final Pipe     connectPipe;
        private final Pipe     bindPipe;

        public PendingConnection(Endpoint endpoint, Pipe connectPipe, Pipe bindPipe)
        {
            super();
            this.endpoint = endpoint;
            this.connectPipe = connectPipe;
            this.bindPipe = bindPipe;
        }
    }

    private enum Side
    {
        CONNECT,
        BIND
    }

    //  Used to check whether the object is a context.
    private int tag;

    //  Sockets belonging to this context. We need the list so that
    //  we can notify the sockets when zmq_term() is called. The sockets
    //  will return ETERM then.
    private final List<SocketBase> sockets;

    //  List of unused thread slots.
    private final Deque<Integer> emptySlots;

    //  If true, init has been called but no socket has been created
    //  yet. Launching of I/O threads is delayed.
    private final AtomicBoolean starting = new AtomicBoolean(true);

    //  If true, zmq_term was already called.
    private boolean terminating;

    //  Synchronization of accesses to global slot-related data:
    //  sockets, emptySlots, terminating. It also synchronizes
    //  access to zombie sockets as such (as opposed to slots) and provides
    //  a memory barrier to ensure that all CPU cores see the same data.
    private final Lock slotSync;

    // A list of poll selectors opened under this context. When the context is
    // destroyed, each of the selectors is closed to ensure resource
    // deallocation.
    private final List<Selector> selectors = new ArrayList<>();

    //  The reaper thread.
    private Reaper reaper;

    //  I/O threads.
    private final List<IOThread> ioThreads;

    //  Array of pointers to mailboxes for both application and I/O threads.
    private int       slotCount;
    private Mailbox[] slots;

    //  Mailbox for zmq_term thread.
    private final Mailbox termMailbox;

    //  List of inproc endpoints within this context.
    private final Map<String, Endpoint> endpoints;

    //  Synchronization of access to the list of inproc endpoints.
    private final Lock endpointsSync;

    //  Maximum socket ID.
    private static AtomicInteger maxSocketId = new AtomicInteger(0);

    //  Maximum number of sockets that can be opened at the same time.
    private int maxSockets;

    //  Number of I/O threads to launch.
    private int ioThreadCount;

    //  Does context wait (possibly forever) on termination?
    private boolean blocky;

    //  Synchronization of access to context options.
    private final Lock optSync;

    //  Synchronization of access to selectors.
    private final Lock selectorSync = new ReentrantLock();

    static final int         TERM_TID   = 0;
    private static final int REAPER_TID = 1;

    private final Map<String, PendingConnection> pendingConnections = new HashMap<>();

    private boolean ipv6;

    private final Errno errno = new Errno();

    public Ctx()
    {
        tag = 0xabadcafe;
        terminating = false;
        reaper = null;
        slotCount = 0;
        slots = null;
        maxSockets = ZMQ.ZMQ_MAX_SOCKETS_DFLT;
        ioThreadCount = ZMQ.ZMQ_IO_THREADS_DFLT;

        ipv6 = false;
        blocky = true;
        slotSync = new ReentrantLock();
        endpointsSync = new ReentrantLock();
        optSync = new ReentrantLock();

        termMailbox = new Mailbox(this, "terminater", -1);

        emptySlots = new ArrayDeque<>();
        ioThreads = new ArrayList<>();
        sockets = new ArrayList<>();
        endpoints = new HashMap<>();
    }

    private void destroy() throws IOException
    {
        assert (sockets.isEmpty());

        for (IOThread it : ioThreads) {
            it.stop();
        }
        for (IOThread it : ioThreads) {
            it.close();
        }
        ioThreads.clear();

        selectorSync.lock();
        try {
            for (Selector selector : selectors) {
                if (selector != null) {
                    selector.close();
                }
            }
            selectors.clear();
        }
        finally {
            selectorSync.unlock();
        }

        //  Deallocate the reaper thread object.
        if (reaper != null) {
            reaper.close();
        }
        //  Deallocate the array of mailboxes. No special work is
        //  needed as mailboxes themselves were deallocated with their
        //  corresponding io_thread/socket objects.
        termMailbox.close();

        tag = 0xdeadbeef;
    }

    //  Returns false if object is not a context.
    //
    //  This will also return false if terminate() has been called.
    public boolean checkTag()
    {
        return tag == 0xabadcafe;
    }

    //  This function is called when user invokes zmq_term. If there are
    //  no more sockets open it'll cause all the infrastructure to be shut
    //  down. If there are open sockets still, the deallocation happens
    //  after the last one is closed.

    public void terminate()
    {
        // Connect up any pending inproc connections, otherwise we will hang
        for (Entry<String, PendingConnection> pending : pendingConnections.entrySet()) {
            SocketBase s = createSocket(ZMQ.ZMQ_PAIR);
            s.bind(pending.getKey());
            s.close();
        }

        slotSync.lock();
        try {
            if (!starting.get()) {
                //  Check whether termination was already underway, but interrupted and now
                //  restarted.
                boolean restarted = terminating;
                terminating = true;

                //  First attempt to terminate the context.
                if (!restarted) {
                    //  First send stop command to sockets so that any blocking calls
                    //  can be interrupted. If there are no sockets we can ask reaper
                    //  thread to stop.
                    for (SocketBase socket : sockets) {
                        socket.stop();
                    }
                    if (sockets.isEmpty()) {
                        reaper.stop();
                    }
                }
                slotSync.unlock();

                //  Wait till reaper thread closes all the sockets.
                Command cmd = termMailbox.recv(WAIT_FOREVER);
                if (cmd == null) {
                    throw new IllegalStateException();
                }
                assert (cmd.type == Command.Type.DONE);
                slotSync.lock();
                assert (sockets.isEmpty());
            }
        }
        finally {
            slotSync.unlock();
        }

        //  Deallocate the resources.
        try {
            destroy();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    final void shutdown()
    {
        slotSync.lock();
        try {
            if (!starting.get() && !terminating) {
                terminating = true;
                //  Send stop command to sockets so that any blocking calls
                //  can be interrupted. If there are no sockets we can ask reaper
                //  thread to stop.
                for (SocketBase socket : sockets) {
                    socket.stop();
                }
                if (sockets.isEmpty()) {
                    reaper.stop();
                }

            }
        }
        finally {
            slotSync.unlock();
        }
    }

    public boolean set(int option, int optval)
    {
        if (option == ZMQ.ZMQ_MAX_SOCKETS && optval >= 1) {
            optSync.lock();
            try {
                maxSockets = optval;
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_IO_THREADS && optval >= 0) {
            optSync.lock();
            try {
                ioThreadCount = optval;
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_BLOCKY && optval >= 0) {
            optSync.lock();
            try {
                blocky = (optval != 0);
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_IPV6 && optval >= 0) {
            optSync.lock();
            try {
                ipv6 = (optval != 0);
            }
            finally {
                optSync.unlock();
            }
        }
        else {
            return false;
        }
        return true;
    }

    public int get(int option)
    {
        int rc;
        if (option == ZMQ.ZMQ_MAX_SOCKETS) {
            rc = maxSockets;
        }
        else if (option == ZMQ.ZMQ_IO_THREADS) {
            rc = ioThreadCount;
        }
        else if (option == ZMQ.ZMQ_BLOCKY) {
            rc = blocky ? 1 : 0;
        }
        else if (option == ZMQ.ZMQ_IPV6) {
            rc = ipv6 ? 1 : 0;
        }
        else {
            throw new IllegalArgumentException("option = " + option);
        }
        return rc;
    }

    public SocketBase createSocket(int type)
    {
        SocketBase s = null;
        slotSync.lock();
        try {
            if (starting.compareAndSet(true, false)) {
                initSlots();
            }

            //  Once zmq_term() was called, we can't create new sockets.
            if (terminating) {
                throw new ZError.CtxTerminatedException();
            }

            //  If maxSockets limit was reached, return error.
            if (emptySlots.isEmpty()) {
                throw new IllegalStateException("EMFILE");
            }

            //  Choose a slot for the socket.
            int slot = emptySlots.pollLast();

            //  Generate new unique socket ID.
            int sid = maxSocketId.incrementAndGet();

            //  Create the socket and register its mailbox.
            s = Sockets.create(type, this, slot, sid);
            if (s == null) {
                emptySlots.addLast(slot);
                return null;
            }
            sockets.add(s);
            slots[slot] = s.getMailbox();
        }
        finally {
            slotSync.unlock();
        }

        return s;
    }

    private void initSlots()
    {
        slotSync.lock();
        try {
            //  Initialize the array of mailboxes. Additional two slots are for
            //  zmq_term thread and reaper thread.
            int ios;
            optSync.lock();
            try {
                ios = ioThreadCount;
                slotCount = maxSockets + ioThreadCount + 2;
            }
            finally {
                optSync.unlock();
            }
            slots = new Mailbox[slotCount];

            //  Initialize the infrastructure for zmq_term thread.
            slots[TERM_TID] = termMailbox;

            //  Create the reaper thread.
            reaper = new Reaper(this, REAPER_TID);
            slots[REAPER_TID] = reaper.getMailbox();
            reaper.start();

            //  Create I/O thread objects and launch them.
            for (int i = 2; i != ios + 2; i++) {
                IOThread ioThread = new IOThread(this, i);
                //alloc_assert (io_thread);
                ioThreads.add(ioThread);
                slots[i] = ioThread.getMailbox();
                ioThread.start();
            }

            //  In the unused part of the slot array, create a list of empty slots.
            for (int i = slotCount - 1; i >= ios + 2; i--) {
                emptySlots.add(i);
                slots[i] = null;
            }
        }
        finally {
            slotSync.unlock();
        }
    }

    void destroySocket(SocketBase socket)
    {
        slotSync.lock();

        //  Free the associated thread slot.
        try {
            int tid = socket.getTid();
            emptySlots.add(tid);
            slots[tid] = null;

            //  Remove the socket from the list of sockets.
            sockets.remove(socket);

            //  If zmq_term() was already called and there are no more socket
            //  we can ask reaper thread to terminate.
            if (terminating && sockets.isEmpty()) {
                reaper.stop();
            }
        }
        finally {
            slotSync.unlock();
        }
    }

    // Creates a Selector that will be closed when the context is destroyed.
    public Selector createSelector()
    {
        selectorSync.lock();
        try {
            Selector selector = Selector.open();
            assert (selector != null);
            selectors.add(selector);
            return selector;
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
        finally {
            selectorSync.unlock();
        }
    }

    public boolean closeSelector(Selector selector)
    {
        selectorSync.lock();
        try {
            boolean rc = selectors.remove(selector);
            if (rc) {
                try {
                    selector.close();
                }
                catch (IOException e) {
                    throw new ZError.IOException(e);
                }
            }
            return rc;
        }
        finally {
            selectorSync.unlock();
        }
    }

    //  Returns reaper thread object.
    ZObject getReaper()
    {
        return reaper;
    }

    //  Send command to the destination thread.
    void sendCommand(int tid, final Command command)
    {
        //        System.out.println(Thread.currentThread().getName() + ": Sending command " + command);
        slots[tid].send(command);
    }

    //  Returns the I/O thread that is the least busy at the moment.
    //  Affinity specifies which I/O threads are eligible (0 = all).
    //  Returns NULL if no I/O thread is available.
    IOThread chooseIoThread(long affinity)
    {
        if (ioThreads.isEmpty()) {
            return null;
        }

        //  Find the I/O thread with minimum load.
        int minLoad = -1;
        IOThread selectedIoThread = null;

        for (int i = 0; i != ioThreads.size(); i++) {
            if (affinity == 0 || (affinity & (1L << i)) > 0) {
                int load = ioThreads.get(i).getLoad();
                if (selectedIoThread == null || load < minLoad) {
                    minLoad = load;
                    selectedIoThread = ioThreads.get(i);
                }
            }
        }
        return selectedIoThread;
    }

    //  Management of inproc endpoints.
    boolean registerEndpoint(String addr, Endpoint endpoint)
    {
        endpointsSync.lock();

        Endpoint inserted = null;
        try {
            inserted = endpoints.put(addr, endpoint);
        }
        finally {
            endpointsSync.unlock();
        }
        if (inserted != null) {
            return false;
        }
        return true;
    }

    boolean unregisterEndpoint(String addr, SocketBase socket)
    {
        endpointsSync.lock();

        try {
            Endpoint endpoint = endpoints.get(addr);
            if (endpoint != null && socket == endpoint.socket) {
                endpoints.remove(addr);
                return true;
            }
        }
        finally {
            endpointsSync.unlock();
        }
        return false;
    }

    void unregisterEndpoints(SocketBase socket)
    {
        endpointsSync.lock();

        try {
            Iterator<Entry<String, Endpoint>> it = endpoints.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Endpoint> e = it.next();
                if (e.getValue().socket == socket) {
                    it.remove();
                }
            }
        }
        finally {
            endpointsSync.unlock();
        }
    }

    Endpoint findEndpoint(String addr)
    {
        Endpoint endpoint = null;
        endpointsSync.lock();

        try {
            endpoint = endpoints.get(addr);
            if (endpoint == null) {
                return new Endpoint(null, new Options());
            }

            //  Increment the command sequence number of the peer so that it won't
            //  get deallocated until "bind" command is issued by the caller.
            //  The subsequent 'bind' has to be called with inc_seqnum parameter
            //  set to false, so that the seqnum isn't incremented twice.
            endpoint.socket.incSeqnum();
        }
        finally {
            endpointsSync.unlock();
        }
        return endpoint;
    }

    void pendConnection(String addr, Endpoint endpoint, Pipe[] pipes)
    {
        PendingConnection pendingConnection = new PendingConnection(endpoint, pipes[0], pipes[1]);
        endpointsSync.lock();
        try {
            Endpoint existing = endpoints.get(addr);
            if (existing == null) {
                // Still no bind.
                endpoint.socket.incSeqnum();
                pendingConnections.put(addr, pendingConnection);
            }
            else {
                // Bind has happened in the mean time, connect directly
                connectInprocSockets(existing.socket, existing.options, pendingConnection, Side.CONNECT);
            }
        }
        finally {
            endpointsSync.unlock();
        }
    }

    void connectPending(String addr, SocketBase bindSocket)
    {
        endpointsSync.lock();
        try {
            PendingConnection pending = pendingConnections.remove(addr);
            if (pending != null) {
                connectInprocSockets(bindSocket, endpoints.get(addr).options, pending, Side.BIND);
            }
        }
        finally {
            endpointsSync.unlock();
        }
    }

    private void connectInprocSockets(SocketBase bindSocket, Options bindOptions, PendingConnection pendingConnection,
                                      Side side)
    {
        bindSocket.incSeqnum();

        pendingConnection.bindPipe.setTid(bindSocket.getTid());
        if (!bindOptions.recvIdentity) {
            Msg msg = pendingConnection.bindPipe.read();
            assert (msg != null);
        }

        int sndhwm = 0;
        if (pendingConnection.endpoint.options.sendHwm != 0 && bindOptions.recvHwm != 0) {
            sndhwm = pendingConnection.endpoint.options.sendHwm + bindOptions.recvHwm;
        }
        int rcvhwm = 0;
        if (pendingConnection.endpoint.options.recvHwm != 0 && bindOptions.sendHwm != 0) {
            rcvhwm = pendingConnection.endpoint.options.recvHwm + bindOptions.sendHwm;
        }
        boolean conflate = pendingConnection.endpoint.options.conflate
                && (pendingConnection.endpoint.options.type == ZMQ.ZMQ_DEALER
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PULL
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PUSH
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PUB
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_SUB);
        int[] hwms = { conflate ? -1 : sndhwm, conflate ? -1 : rcvhwm };
        pendingConnection.connectPipe.setHwms(hwms[1], hwms[0]);
        pendingConnection.bindPipe.setHwms(hwms[0], hwms[1]);

        if (side == Side.BIND) {
            Command cmd = new Command(null, Command.Type.BIND, pendingConnection.bindPipe);
            bindSocket.processCommand(cmd);
            bindSocket.sendInprocConnected(pendingConnection.endpoint.socket);
        }
        else {
            pendingConnection.connectPipe.sendBind(bindSocket, pendingConnection.bindPipe, false);
        }

        // When a ctx is terminated all pending inproc connection will be
        // connected, but the socket will already be closed and the pipe will be
        // in waiting_for_delimiter state, which means no more writes can be done
        // and the identity write fails and causes an assert. Check if the socket
        // is open before sending.
        if (pendingConnection.endpoint.options.recvIdentity && pendingConnection.endpoint.socket.checkTag()) {
            Msg id = new Msg(bindOptions.identitySize);
            id.put(bindOptions.identity, 0, bindOptions.identitySize);
            id.setFlags(Msg.IDENTITY);
            boolean written = pendingConnection.bindPipe.write(id);
            assert (written);
            pendingConnection.bindPipe.flush();
        }
    }

    public Errno errno()
    {
        return errno;
    }
}
