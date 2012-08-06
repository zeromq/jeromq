package zmq;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class Own extends ZObject {

	protected final Options options;
	
    //  True if termination was already initiated. If so, we can destroy
    //  the object if there are no more child objects or pending term acks.
    private boolean terminating;

    //  Sequence number of the last command sent to this object.
    final private AtomicLong sent_seqnum;

    //  Sequence number of the last command processed by this object.
    private long processed_seqnum;

    //  Socket owning this object. It's responsible for shutting down
    //  this object.
    private Own owner;

    //  List of all objects owned by this socket. We are responsible
    //  for deallocating them before we quit.
    //typedef std::set <own_t*> owned_t;
    final private Set<Own> owned;

    //  Number of events we have to get before we can destroy the object.
    private int term_acks;

    
	public Own(Ctx parent_, int tid_) {
	    super (parent_, tid_);
	    terminating = false;
	    sent_seqnum = new AtomicLong(0);
	    processed_seqnum = 0;
	    owner = null;
	    term_acks = 0 ;
	    
	    options = new Options();
	    owned = new HashSet<Own>();
	}

	public Own(IOThread io_thread_, Options options_) {
	    super (io_thread_);
	    options = options_;
	    terminating = false;
        sent_seqnum = new AtomicLong(0);
        processed_seqnum = 0;
        owner = null;
        term_acks = 0 ;
        
        owned = new HashSet<Own>();
    }
	

    void inc_seqnum ()
	{
	    //  This function may be called from a different thread!
	    sent_seqnum.incrementAndGet();
	}
	
	boolean is_terminating ()
	{
	    return terminating;
	}
	
	void register_term_acks (int count_)
	{
	    term_acks += count_;
	}

	void launch_child (Own object_)
	{
	    //  Specify the owner of the object.
	    object_.set_owner (this);

	    //  Plug the object into the I/O thread.
	    send_plug (object_);

	    //  Take ownership of the object.
	    send_own (this, object_);
	}
	
	void set_owner (Own owner_)
	{
	    assert (owner == null);
	    owner = owner_;
	}

	protected void process_own (Own object_)
	{
	    //  If the object is already being shut down, new owned objects are
	    //  immediately asked to terminate. Note that linger is set to zero.
	    if (terminating) {
	        register_term_acks (1);
	        send_term (object_, 0);
	        return;
	    }

	    //  Store the reference to the owned object.
	    owned.add (object_);
	}

	protected void process_seqnum ()
	{
	    //  Catch up with counter of processed commands.
	    processed_seqnum++;

	    //  We may have catched up and still have pending terms acks.
	    check_term_acks ();
	}
	
	void check_term_acks ()
	{
	    if (terminating && processed_seqnum == sent_seqnum.get () &&
	          term_acks == 0) {

	        //  Sanity check. There should be no active children at this point.
	        assert (owned.isEmpty ());

	        //  The root object has nobody to confirm the termination to.
	        //  Other nodes will confirm the termination to the owner.
	        if (owner != null)
	            send_term_ack (owner);

	        //  Deallocate the resources.
	        process_destroy ();
	    }
	}
	
	void terminate ()
	{
	    //  If termination is already underway, there's no point
	    //  in starting it anew.
	    if (terminating)
	        return;

	    //  As for the root of the ownership tree, there's noone to terminate it,
	    //  so it has to terminate itself.
	    if (owner == null) {
	        process_term (options.linger);
	        return;
	    }

	    //  If I am an owned object, I'll ask my owner to terminate me.
	    send_term_req (owner, this);
	}
	
	protected void process_term (int linger_)
	{
	    //  Double termination should never happen.
	    assert (!terminating);

	    //  Send termination request to all owned objects.
	    for (Own it : owned)
	        send_term (it, linger_);
	    register_term_acks (owned.size ());
	    owned.clear ();

	    //  Start termination process and check whether by chance we cannot
	    //  terminate immediately.
	    terminating = true;
	    check_term_acks ();
	}


	protected void process_term_ack ()
	{
	    unregister_term_ack ();
	    
	}

	
	protected void unregister_term_ack() {
        assert (term_acks > 0);
        term_acks--;

        //  This may be a last ack we are waiting for before termination...
        check_term_acks ();
    }

    void process_destroy ()
	{
	}




}
