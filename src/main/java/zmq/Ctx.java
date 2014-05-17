/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
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

public class Ctx {
    
    //  Information associated with inproc endpoint. Note that endpoint options
    //  are registered as well so that the peer can access them without a need
    //  for synchronisation, handshaking or similar.
    
    public static class Endpoint {
        SocketBase socket;
        Options options;
        
        public Endpoint(SocketBase socket_, Options options_) {
            socket = socket_;
            options = options_;
        }

    }
    //  Used to check whether the object is a context.
    private int tag;

    //  Sockets belonging to this context. We need the list so that
    //  we can notify the sockets when zmq_term() is called. The sockets
    //  will return ETERM then.
    private final List<SocketBase> sockets;

    //  List of unused thread slots.
    private final Deque<Integer> empty_slots;

    //  If true, zmq_init has been called but no socket has been created
    //  yet. Launching of I/O threads is delayed.
    private AtomicBoolean starting = new AtomicBoolean(true);

    //  If true, zmq_term was already called.
    private boolean terminating;

    //  Synchronisation of accesses to global slot-related data:
    //  sockets, empty_slots, terminating. It also synchronises
    //  access to zombie sockets as such (as opposed to slots) and provides
    //  a memory barrier to ensure that all CPU cores see the same data.
    private final Lock slot_sync;

    //  The reaper thread.
    private Reaper reaper;

    //  I/O threads.
    private final List<IOThread> io_threads;

    //  Array of pointers to mailboxes for both application and I/O threads.
    private int slot_count;
    private Mailbox[] slots;

    //  Mailbox for zmq_term thread.
    private final Mailbox term_mailbox;

    //  List of inproc endpoints within this context.
    private final Map<String, Endpoint> endpoints;

    //  Synchronisation of access to the list of inproc endpoints.
    private final Lock endpoints_sync;

    //  Maximum socket ID.
    private static AtomicInteger max_socket_id = new AtomicInteger(0);

    //  Maximum number of sockets that can be opened at the same time.
    private int max_sockets;

    //  Number of I/O threads to launch.
    private int io_thread_count;

    //  Synchronisation of access to context options.
    private final Lock opt_sync;

    public static final int TERM_TID = 0;
    public static final int REAPER_TID = 1;
    
    public Ctx ()
    {
        tag = 0xabadcafe;
        terminating = false;
        reaper = null;
        slot_count = 0;
        slots = null;
        max_sockets = ZMQ.ZMQ_MAX_SOCKETS_DFLT;
        io_thread_count = ZMQ.ZMQ_IO_THREADS_DFLT;
        
        slot_sync = new ReentrantLock();
        endpoints_sync = new ReentrantLock();
        opt_sync = new ReentrantLock();
        
        term_mailbox = new Mailbox("terminater");
        
        empty_slots = new ArrayDeque<Integer>();
        io_threads = new ArrayList<IOThread>();
        sockets = new ArrayList<SocketBase>();
        endpoints = new HashMap<String, Endpoint>();

    }
    
    protected void destroy() {
        
        for  (IOThread it: io_threads) {
            it.stop();
        }
        for  (IOThread it: io_threads) {
            it.destroy();
        }

        if (reaper != null)
            reaper.destroy();
        term_mailbox.close();
        
        tag = 0xdeadbeef;
    }
    
    //  Returns false if object is not a context.
    public boolean check_tag ()
    {
        return tag == 0xabadcafe;
    }
    
    //  This function is called when user invokes zmq_term. If there are
    //  no more sockets open it'll cause all the infrastructure to be shut
    //  down. If there are open sockets still, the deallocation happens
    //  after the last one is closed.
    
    public void terminate() {
        
        tag = 0xdeadbeef;

        if (!starting.get()) {
            slot_sync.lock ();
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
                    for(SocketBase socket: sockets) {
                        socket.stop();
                    }
                    if (sockets.isEmpty ())
                        reaper.stop ();
                }
            } finally {
                slot_sync.unlock();
            }
            //  Wait till reaper thread closes all the sockets.
            Command cmd = term_mailbox.recv (-1);
            if (cmd == null)
                throw new IllegalStateException();
            assert (cmd.type() == Command.Type.DONE);
            slot_sync.lock ();
            try {
                assert (sockets.isEmpty ());
            } finally {
                slot_sync.unlock ();
            }
        }

        //  Deallocate the resources.
        destroy();
    }
    
    public boolean set (int option_, int optval_)
    {
        if (option_ == ZMQ.ZMQ_MAX_SOCKETS && optval_ >= 1) {
            opt_sync.lock ();
            try {
                max_sockets = optval_;
            } finally {
                opt_sync.unlock ();
            }
        }
        else
        if (option_ == ZMQ.ZMQ_IO_THREADS && optval_ >= 0) {
            opt_sync.lock ();
            try {
                io_thread_count = optval_;
            } finally {
                opt_sync.unlock ();
            }
        }
        else {
            return false;
        }
        return true;
    }

    public int get (int option_)
    {
        int rc = 0;
        if (option_ == ZMQ.ZMQ_MAX_SOCKETS)
            rc = max_sockets;
        else
        if (option_ == ZMQ.ZMQ_IO_THREADS)
            rc = io_thread_count;
        else {
            throw new IllegalArgumentException("option = " + option_);
        }
        return rc;
    }
    
    public SocketBase create_socket (int type_)
    {
        SocketBase s = null;
        slot_sync.lock ();
        try {
            if (starting.compareAndSet(true, false)) {
                //  Initialise the array of mailboxes. Additional three slots are for
                //  zmq_term thread and reaper thread.
                int mazmq;
                int ios;
                opt_sync.lock ();
                try {
                    mazmq = max_sockets;
                    ios = io_thread_count;
                } finally {
                    opt_sync.unlock ();
                }
                slot_count = mazmq + ios + 2;
                slots = new Mailbox[slot_count];
                //alloc_assert (slots);
    
                //  Initialise the infrastructure for zmq_term thread.
                slots [TERM_TID] = term_mailbox;
    
                //  Create the reaper thread.
                reaper = new Reaper (this, REAPER_TID);
                //alloc_assert (reaper);
                slots [REAPER_TID] = reaper.get_mailbox ();
                reaper.start ();
    
                //  Create I/O thread objects and launch them.
                for (int i = 2; i != ios + 2; i++) {
                    IOThread io_thread = new IOThread (this, i);
                    //alloc_assert (io_thread);
                    io_threads.add(io_thread);
                    slots [i] = io_thread.get_mailbox ();
                    io_thread.start ();
                }
    
                //  In the unused part of the slot array, create a list of empty slots.
                for (int i = (int) slot_count - 1;
                      i >= (int) ios + 2; i--) {
                    empty_slots.add (i);
                    slots [i] = null;
                }
                
            }
    
            //  Once zmq_term() was called, we can't create new sockets.
            if (terminating) {
                throw new ZError.CtxTerminatedException();
            }
    
            //  If max_sockets limit was reached, return error.
            if (empty_slots.isEmpty ()) {
                throw new IllegalStateException("EMFILE");
            }
    
            //  Choose a slot for the socket.
            int slot = empty_slots.pollLast();
    
            //  Generate new unique socket ID.
            int sid = max_socket_id.incrementAndGet();
    
            //  Create the socket and register its mailbox.
            s = SocketBase.create (type_, this, slot, sid);
            if (s == null) {
                empty_slots.addLast(slot);
                return null;
            }
            sockets.add (s);
            slots [slot] = s.get_mailbox ();
            
        } finally {
            slot_sync.unlock ();
        }
        
        return s;
    }
    
    
    public void destroy_socket(SocketBase socket_) {
        slot_sync.lock ();

        //  Free the associated thread slot.
        try {
            int tid = socket_.get_tid ();
            empty_slots.add (tid);
            slots [tid].close();
            slots [tid] = null;
    
            //  Remove the socket from the list of sockets.
            sockets.remove (socket_);
    
            //  If zmq_term() was already called and there are no more socket
            //  we can ask reaper thread to terminate.
            if (terminating && sockets.isEmpty ())
                reaper.stop ();
        } finally {
            slot_sync.unlock ();
        }
    }
    
    //  Returns reaper thread object.
    public ZObject get_reaper() {
        return reaper;
    }
    
    //  Send command to the destination thread.
    public void send_command (int tid_, final Command command_)
    {
        slots [tid_].send (command_);
    }
    
    //  Returns the I/O thread that is the least busy at the moment.
    //  Affinity specifies which I/O threads are eligible (0 = all).
    //  Returns NULL if no I/O thread is available.
    public IOThread choose_io_thread(long affinity_) {
        if (io_threads.isEmpty ())
            return null;

        //  Find the I/O thread with minimum load.
        int min_load = -1;
        IOThread selected_io_thread = null;

        for (int i = 0; i != io_threads.size (); i++) {
            if (affinity_ == 0 || (affinity_ & ( 1L << i)) > 0) {
                int load = io_threads .get(i).get_load ();
                if (selected_io_thread == null || load < min_load) {
                    min_load = load;
                    selected_io_thread = io_threads.get(i);
                }
            }
        }
        return selected_io_thread;
    }
    
    //  Management of inproc endpoints.
    public boolean register_endpoint(String addr_, Endpoint endpoint_) {
        endpoints_sync.lock ();

        Endpoint inserted = null;
        try {
            inserted = endpoints.put(addr_, endpoint_);
        } finally {
            endpoints_sync.unlock ();
        }
        if (inserted != null) {
            return false;
        }
        return true;
    }

    public void unregister_endpoints(SocketBase socket_) {
        
        endpoints_sync.lock ();

        try {
            Iterator<Entry<String, Endpoint>> it = endpoints.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Endpoint> e = it.next();
                if (e.getValue().socket == socket_) {
                    it.remove();
                    continue;
                }
            }
        } finally {
            endpoints_sync.unlock ();
        }
    }
    

    public Endpoint find_endpoint (String addr_)
    {
        Endpoint endpoint = null;
        endpoints_sync.lock ();

        try {
            endpoint = endpoints.get(addr_);
            if (endpoint == null) {
                //ZError.errno(ZError.ECONNREFUSED);
                return new Endpoint(null, new Options());
            }
    
            //  Increment the command sequence number of the peer so that it won't
            //  get deallocated until "bind" command is issued by the caller.
            //  The subsequent 'bind' has to be called with inc_seqnum parameter
            //  set to false, so that the seqnum isn't incremented twice.
            endpoint.socket.inc_seqnum ();
        } finally {
            endpoints_sync.unlock ();
        }
        return endpoint;
    }
}
