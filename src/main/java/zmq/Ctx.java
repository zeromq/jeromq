package zmq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import zmq.Ctx.Endpoint;


public class Ctx {
	
    private static Logger LOG = LoggerFactory.getLogger(Ctx.class);

    public static class Endpoint {
		SocketBase socket;
        Options options;
        
        public Endpoint(SocketBase socket_, Options options_) {
            socket = socket_;
            options = options_;
        }

	}
//  Used to check whether the object is a context.
    final long tag;

    //  Sockets belonging to this context. We need the list so that
    //  we can notify the sockets when zmq_term() is called. The sockets
    //  will return ETERM then.
    //typedef array_t <socket_base_t> sockets_t;
    final List<SocketBase> sockets;

    //  List of unused thread slots.
    //typedef std::vector <uint32_t> emtpy_slots_t;
    final Deque<Integer> empty_slots;

    //  If true, zmq_init has been called but no socket has been created
    //  yet. Launching of I/O threads is delayed.
    volatile boolean starting;

    //  If true, zmq_term was already called.
    boolean terminating;

    //  Synchronisation of accesses to global slot-related data:
    //  sockets, empty_slots, terminating. It also synchronises
    //  access to zombie sockets as such (as opposed to slots) and provides
    //  a memory barrier to ensure that all CPU cores see the same data.
    final Lock slot_sync;

    //  The reaper thread.
    Reaper reaper;

    //  I/O threads.
    //typedef std::vector <zmq::io_thread_t*> io_threads_t;
    final List<IOThread> io_threads;

    //  Array of pointers to mailboxes for both application and I/O threads.
    int slot_count;
    Mailbox[] slots;

    //  Mailbox for zmq_term thread.
    final Mailbox term_mailbox;

    //  List of inproc endpoints within this context.
    //typedef std::map <std::string, endpoint_t> endpoints_t;
    final Map<String, Endpoint> endpoints;

    //  Synchronisation of access to the list of inproc endpoints.
    final Lock endpoints_sync;

    //  Maximum socket ID.
    static AtomicInteger max_socket_id = new AtomicInteger(0);

    //  Maximum number of sockets that can be opened at the same time.
    int max_sockets;

    //  Number of I/O threads to launch.
    int io_thread_count;

    //  Synchronisation of access to context options.
    final Lock opt_sync;

    // Monitoring callback
    //zmq_monitor_fn *monitor_fn;
    IZmqMonitor monitor_fn;
    
    public static final int term_tid = 0;
    public static final int reaper_tid = 1;
    
    public Ctx ()
    {
        tag = 0xabadcafe;
        starting = true;
        terminating = false;
        reaper = null;
        slot_count = 0;
        slots = null;
        max_sockets = ZMQ.ZMQ_MAX_SOCKETS_DFLT;
        io_thread_count = ZMQ.ZMQ_IO_THREADS_DFLT;
        monitor_fn = null;
        
        slot_sync = new ReentrantLock();
        endpoints_sync = new ReentrantLock();
        opt_sync = new ReentrantLock();
        
        term_mailbox = new Mailbox("terminater");
        
        empty_slots = new ArrayDeque<Integer>();
        io_threads = new ArrayList<IOThread>();
        sockets = new ArrayList<SocketBase>();
        endpoints = new HashMap<String, Endpoint>();
    }
    public boolean check_tag ()
    {
        return tag == 0xabadcafe;
    }
    
    public void set (int option_, int optval_)
    {
        if (option_ == ZMQ.ZMQ_MAX_SOCKETS && optval_ >= 1) {
            opt_sync.lock ();
            max_sockets = optval_;
            opt_sync.unlock ();
        }
        else
        if (option_ == ZMQ.ZMQ_IO_THREADS && optval_ >= 0) {
            opt_sync.lock ();
            io_thread_count = optval_;
            opt_sync.unlock ();
        }
        else {
            throw new IllegalArgumentException("option = " + option_);
        }
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
            if (starting) {
    
                starting = false;
                //  Initialise the array of mailboxes. Additional three slots are for
                //  zmq_term thread and reaper thread.
                opt_sync.lock ();
                int mazmq = max_sockets;
                int ios = io_thread_count;
                opt_sync.unlock ();
                slot_count = mazmq + ios + 2;
                slots = new Mailbox[slot_count];
                //alloc_assert (slots);
    
                //  Initialise the infrastructure for zmq_term thread.
                slots [term_tid] = term_mailbox;
    
                //  Create the reaper thread.
                reaper = new Reaper (this, reaper_tid);
                //alloc_assert (reaper);
                slots [reaper_tid] = reaper.get_mailbox ();
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
                
                if (LOG.isDebugEnabled()) {
                    for (int i = 0; i < slot_count; i++) {
                        Mailbox box = slots[i];
                        if (box == null) break;
                        LOG.debug("[" + i + "] " + box);
                    }
                }
            }
    
            //  Once zmq_term() was called, we can't create new sockets.
            if (terminating) {
                throw new IllegalStateException();
            }
    
            //  If max_sockets limit was reached, return error.
            if (empty_slots.isEmpty ()) {
                Errno.set(Errno.EMFILE);
                throw new ZException.NoFreeSlot();
            }
    
            //  Choose a slot for the socket.
            int slot = empty_slots.peekLast();
            //uint32_t slot = empty_slots..back ();
            //empty_slots.pop_back ();
    
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
            
            LOG.debug("NEW Slot [" + slot + "] " + slots [slot]);
        } finally {
            slot_sync.unlock ();
        }
        
        return s;
    }
    
    Endpoint find_endpoint (String addr_)
    {
        Endpoint endpoint = null;
        endpoints_sync.lock ();

        try {
            endpoint = endpoints.get(addr_);
            if (endpoint == null) {
                throw new ZException.ConnectionRefused();
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
    
    void send_command (int tid_, final Command command_)
    {
        slots [tid_].send (command_);
    }
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
    
    public ZObject get_reaper() {
        return reaper;
    }
    
    public int terminate() {
        slot_sync.lock ();
        if (!starting) {

            //  Check whether termination was already underway, but interrupted and now
            //  restarted.
            boolean restarted = terminating;
            terminating = true;
            slot_sync.unlock ();

            //  First attempt to terminate the context.
            if (!restarted) {

                //  First send stop command to sockets so that any blocking calls
                //  can be interrupted. If there are no sockets we can ask reaper
                //  thread to stop.
                slot_sync.lock ();
                for (int i = 0; i != sockets.size (); i++)
                    sockets.get(i).stop ();
                if (sockets.isEmpty ())
                    reaper.stop ();
                slot_sync.unlock ();
            }

            //  Wait till reaper thread closes all the sockets.
            Command cmd;
            cmd = term_mailbox.recv (-1);
            if (cmd == null)
                return -1;
            //Errno.errno_assert (cmd != null);
            assert (cmd.type() == Command.Type.done);
            slot_sync.lock ();
            assert (sockets.isEmpty ());
        }
        slot_sync.unlock ();

        //  Deallocate the resources.

        return 0;

    }
    public void destroy_socket(SocketBase socket_) {
        slot_sync.lock ();

        //  Free the associated thread slot.
        int tid = socket_.get_tid ();
        empty_slots.add (tid);
        slots [tid] = null;

        //  Remove the socket from the list of sockets.
        sockets.remove (socket_);

        //  If zmq_term() was already called and there are no more socket
        //  we can ask reaper thread to terminate.
        if (terminating && sockets.isEmpty ())
            reaper.stop ();

        slot_sync.unlock ();
        
        LOG.debug("Release Slot [" + tid + "] ");
    }
    
    public void monitor_event (SocketBase socket_, int event_, Object ... args_)
    {
        if (monitor_fn != null) {
            monitor_fn.monitor (socket_, event_, args_);
        }
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
    public void register_endpoints(String addr_, Endpoint endpoint_) {
        endpoints_sync.lock ();

        Endpoint inserted = null;
        try {
            inserted = endpoints.put(addr_, endpoint_);
        } finally {
            endpoints_sync.unlock ();
        }
        if (inserted != null) {
            throw new ZException.AddrInUse();
        }
    }

}
