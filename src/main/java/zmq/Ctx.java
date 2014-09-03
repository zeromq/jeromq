/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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

//Context object encapsulates all the global state associated with
//  the library.

public class Ctx
{
    //  Information associated with inproc endpoint. Note that endpoint options
    //  are registered as well so that the peer can access them without a need
    //  for synchronisation, handshaking or similar.

    static class Endpoint
    {
        public final SocketBase socket;
        public final Options options;

        public Endpoint(SocketBase socket, Options options)
        {
            this.socket = socket;
            this.options = options;
        }

    }
    //  Used to check whether the object is a context.
    private int tag;

    //  Sockets belonging to this context. We need the list so that
    //  we can notify the sockets when zmq_term() is called. The sockets
    //  will return ETERM then.
    private final List<SocketBase> sockets;

    //  List of unused thread slots.
    private final Deque<Integer> emptySlots;

    //  If true, zmqInit has been called but no socket has been created
    //  yet. Launching of I/O threads is delayed.
    private AtomicBoolean starting = new AtomicBoolean(true);

    //  If true, zmq_term was already called.
    private boolean terminating;

    //  Synchronisation of accesses to global slot-related data:
    //  sockets, emptySlots, terminating. It also synchronises
    //  access to zombie sockets as such (as opposed to slots) and provides
    //  a memory barrier to ensure that all CPU cores see the same data.
    private final Lock slotSync;

    //  The reaper thread.
    private Reaper reaper;

    //  I/O threads.
    private final List<IOThread> ioThreads;

    //  Array of pointers to mailboxes for both application and I/O threads.
    private int slotCount;
    private Mailbox[] slots;

    //  Mailbox for zmq_term thread.
    private final Mailbox termMailbox;

    //  List of inproc endpoints within this context.
    private final Map<String, Endpoint> endpoints;

    //  Synchronisation of access to the list of inproc endpoints.
    private final Lock endpointsSync;

    //  Maximum socket ID.
    private static AtomicInteger maxSocketId = new AtomicInteger(0);

    //  Maximum number of sockets that can be opened at the same time.
    private int maxSockets;

    //  Number of I/O threads to launch.
    private int ioThreadCount;

    //  Synchronisation of access to context options.
    private final Lock optSync;

    public static final int TERM_TID = 0;
    public static final int REAPER_TID = 1;

    public Ctx()
    {
        tag = 0xabadcafe;
        terminating = false;
        reaper = null;
        slotCount = 0;
        slots = null;
        maxSockets = ZMQ.ZMQ_MAX_SOCKETS_DFLT;
        ioThreadCount = ZMQ.ZMQ_IO_THREADS_DFLT;

        slotSync = new ReentrantLock();
        endpointsSync = new ReentrantLock();
        optSync = new ReentrantLock();

        termMailbox = new Mailbox("terminater");

        emptySlots = new ArrayDeque<Integer>();
        ioThreads = new ArrayList<IOThread>();
        sockets = new ArrayList<SocketBase>();
        endpoints = new HashMap<String, Endpoint>();
    }

    protected void destroy()
    {
        for (IOThread it : ioThreads) {
            it.stop();
        }
        for (IOThread it : ioThreads) {
            it.destroy();
        }

        if (reaper != null) {
            reaper.destroy();
        }
        termMailbox.close();

        tag = 0xdeadbeef;
    }

    //  Returns false if object is not a context.
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
        tag = 0xdeadbeef;

        if (!starting.get()) {
            slotSync.lock();
            try {
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
            }
            finally {
                slotSync.unlock();
            }
            //  Wait till reaper thread closes all the sockets.
            Command cmd = termMailbox.recv(-1);
            if (cmd == null) {
                throw new IllegalStateException();
            }
            assert (cmd.type() == Command.Type.DONE);
            slotSync.lock();
            try {
                assert (sockets.isEmpty());
            }
            finally {
                slotSync.unlock();
            }
        }

        //  Deallocate the resources.
        destroy();
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
        else
        if (option == ZMQ.ZMQ_IO_THREADS && optval >= 0) {
            optSync.lock();
            try {
                ioThreadCount = optval;
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
        int rc = 0;
        if (option == ZMQ.ZMQ_MAX_SOCKETS) {
            rc = maxSockets;
        }
        else if (option == ZMQ.ZMQ_IO_THREADS) {
            rc = ioThreadCount;
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
                //  Initialise the array of mailboxes. Additional three slots are for
                //  zmq_term thread and reaper thread.
                int mazmq;
                int ios;
                optSync.lock();
                try {
                    mazmq = maxSockets;
                    ios = ioThreadCount;
                }
                finally {
                    optSync.unlock();
                }
                slotCount = mazmq + ios + 2;
                slots = new Mailbox[slotCount];
                //alloc_assert (slots);

                //  Initialise the infrastructure for zmq_term thread.
                slots[TERM_TID] = termMailbox;

                //  Create the reaper thread.
                reaper = new Reaper(this, REAPER_TID);
                //alloc_assert (reaper);
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
                for (int i = (int) slotCount - 1;
                      i >= (int) ios + 2; i--) {
                    emptySlots.add(i);
                    slots[i] = null;
                }
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
            s = SocketBase.create(type, this, slot, sid);
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

    public void destroySocket(SocketBase socket)
    {
        slotSync.lock();

        //  Free the associated thread slot.
        try {
            int tid = socket.getTid();
            emptySlots.add(tid);
            slots[tid].close();
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

    //  Returns reaper thread object.
    public ZObject getReaper()
    {
        return reaper;
    }

    //  Send command to the destination thread.
    void sendCommand(int tid, final Command command)
    {
        slots[tid].send(command);
    }

    //  Returns the I/O thread that is the least busy at the moment.
    //  Affinity specifies which I/O threads are eligible (0 = all).
    //  Returns NULL if no I/O thread is available.
    public IOThread chooseIoThread(long affinity)
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
    public boolean register_endpoint(String addr, Endpoint endpoint)
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

    public void unregisterEndpoints(SocketBase socket)
    {
        endpointsSync.lock();

        try {
            Iterator<Entry<String, Endpoint>> it = endpoints.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Endpoint> e = it.next();
                if (e.getValue().socket == socket) {
                    it.remove();
                    continue;
                }
            }
        }
        finally {
            endpointsSync.unlock();
        }
    }

    public Endpoint findEndpoint(String addr)
    {
        Endpoint endpoint = null;
        endpointsSync.lock();

        try {
            endpoint = endpoints.get(addr);
            if (endpoint == null) {
                //ZError.errno(ZError.ECONNREFUSED);
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
}
