package zmq;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class Own extends ZObject {

	protected final Options options;
	
    //  True if termination was already initiated. If so, we can destroy
    //  the object if there are no more child objects or pending term acks.
    boolean terminating;

    //  Sequence number of the last command sent to this object.
    final AtomicLong sent_seqnum;

    //  Sequence number of the last command processed by this object.
    long processed_seqnum;

    //  Socket owning this object. It's responsible for shutting down
    //  this object.
    Own owner;

    //  List of all objects owned by this socket. We are responsible
    //  for deallocating them before we quit.
    //typedef std::set <own_t*> owned_t;
    final Set<Own> owned;

    //  Number of events we have to get before we can destroy the object.
    int term_acks;

    
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



}
