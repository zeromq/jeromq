package zmq;

import java.util.ArrayDeque;
import java.util.Deque;

public class XPub extends SocketBase {

    public static class XPubSession extends SessionBase {

        public XPubSession(IOThread io_thread_, boolean connect_,
                SocketBase socket_, Options options_, Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }

    }

    //  List of all subscriptions mapped to corresponding pipes.
    final Mtrie subscriptions;

    //  Distributor of messages holding the list of outbound pipes.
    final Dist dist;

    //  True if we are in the middle of sending a multi-part message.
    boolean more;

    //  List of pending (un)subscriptions, ie. those that were already
    //  applied to the trie, but not yet received by the user.
    //typedef std::basic_string <unsigned char> blob_t;
    //typedef std::deque <blob_t> pending_t;
    final Deque<Blob> pending;
    
	public XPub(Ctx parent_, int tid_, int sid_) {
		super (parent_, tid_, sid_);
		
		options.type = ZMQ.ZMQ_XPUB;
		more = false;
		
		subscriptions = new Mtrie();
		dist = new Dist();
		pending = new ArrayDeque<Blob>();
	}
	
	@Override
	protected void xattach_pipe (Pipe pipe_, boolean icanhasall_)
	{
	    assert (pipe_ != null);
	    dist.attach (pipe_);

	    //  If icanhasall_ is specified, the caller would like to subscribe
	    //  to all data on this pipe, implicitly.
	    if (icanhasall_)
	        subscriptions.add (null, pipe_);

	    //  The pipe is active when attached. Let's read the subscriptions from
	    //  it, if any.
	    xread_activated (pipe_);
	}

	@Override
	protected void xread_activated (Pipe pipe_)
	{
	    //  There are some subscriptions waiting. Let's process them.
	    Msg sub = null;
	    while (true) {

	        sub = pipe_.read();
	        //  Grab next subscription.
	        if (sub == null)
	            return;

	        //  Apply the subscription to the trie.
	        //unsigned char *data = (unsigned char*) sub.data ();
	        //byte[] data = new byte[sub.size()];
	        //sub.data(true).get(data);
	        byte[] data = sub.data();
	        int size = sub.size ();
	        if (size > 0 && (data[0] == 0 || data[0] == 1)) {
	            boolean unique;
	            if (data[0] == 0)
	                unique = subscriptions.rm (data , 1, pipe_);
	            else
	                unique = subscriptions.add (data , 1, pipe_);

	            //  If the subscription is not a duplicate, store it so that it can be
	            //  passed to used on next recv call.
	            if (unique && options.type != ZMQ.ZMQ_PUB)
	                pending.add(new Blob (sub.data ()));
	        }

	        //sub.close();
	    }

	}
	
	@Override
	protected void xterminated (Pipe pipe_)
	{
	    //  Remove the pipe from the trie. If there are topics that nobody
	    //  is interested in anymore, send corresponding unsubscriptions
	    //  upstream.
	    
	    Mtrie.IMtrieHandler send_unsubscription = new Mtrie.IMtrieHandler() {
            
            @Override
            public void invoke(Pipe pipe_, byte[] data_, Object arg_) {
                XPub self = (XPub) arg_;

                if (self.options.type != ZMQ.ZMQ_PUB) {

                    //  Place the unsubscription to the queue of pending (un)sunscriptions
                    //  to be retrived by the user later on.
                    Blob unsub = new Blob (data_.length + 1);
                    unsub.put(0,(byte)0);
                    unsub.put(1, data_);
                    self.pending.add (unsub);
                }

            }
        };
	    subscriptions.rm (pipe_, send_unsubscription, this);

	    dist.terminated (pipe_);
	}

	@Override
	protected Msg xrecv(int flags_) {
	    //  If there is at least one 
	    if (pending.isEmpty ()) {
	        return null;
	    }

	    Blob first = pending.pollFirst();
	    //Msg msg_ = new Msg(first.size());
	    //msg_.put(first.data());
	    Msg msg_ = new Msg(first.data());
	    return msg_;

	}

	@Override
	protected boolean xsend(Msg msg_, int flags_) {
	    boolean msg_more = msg_.has_more(); 

	    Mtrie.IMtrieHandler mark_as_matching = new Mtrie.IMtrieHandler() {

            @Override
            public void invoke(Pipe pipe_, byte[] data, Object arg_) {
                XPub self = (XPub) arg_;
                self.dist.match (pipe_);
            }
	        
	    };
	    //  For the first part of multi-part message, find the matching pipes.
	    if (!more)
	        subscriptions.match (msg_.data (), msg_.size(),
	            mark_as_matching, this);

	    //  Send the message to all the pipes that were marked as matching
	    //  in the previous step.
	    boolean rc = dist.send_to_matching (msg_, flags_);
	    if (!rc)
	        return rc;

	    //  If we are at the end of multi-part message we can mark all the pipes
	    //  as non-matching.
	    if (!msg_more)
	        dist.unmatch ();

	    more = msg_more;
	    return true;
	}
	
    @Override
    protected boolean xhas_in() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    protected void xwrite_activated (Pipe pipe_)
    {
        dist.activated (pipe_);
    }

}
