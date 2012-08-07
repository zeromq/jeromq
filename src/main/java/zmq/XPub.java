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
	    Msg sub = new Msg();
	    while (true) {

	        //  Grab next subscription.
	        if (!pipe_.read (sub))
	            return;

	        //  Apply the subscription to the trie.
	        //unsigned char *data = (unsigned char*) sub.data ();
	        byte[] data = sub.data();
	        int size = sub.size ();
	        if (size > 0 && (data[0] == 0 || data[0] == 1)) {
	            boolean unique;
	            if (data[0] == 0)
	                unique = subscriptions.rm (data , 1, pipe_);
	            else
	                unique = subscriptions.add (data , 1, pipe_);

	            //  If the subscription is not a duplicate store it so that it can be
	            //  passed to used on next recv call.
	            if (unique && options.type != ZMQ.ZMQ_PUB)
	                pending.add(new Blob (sub.data ()));
	        }

	        sub.close();
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
            public void removed(byte[] data_, Object arg_) {
                XPub self = (XPub) arg_;

                if (self.options.type != ZMQ.ZMQ_PUB) {

                    //  Place the unsubscription to the queue of pending (un)sunscriptions
                    //  to be retrived by the user later on.
                    Blob unsub = new Blob (data_.length + 1);
                    unsub.put((byte)0);
                    unsub.put(data_);
                    //unsub [0] = 0;
                    //memcpy (&unsub [1], data_, size_);
                    self.pending.add (unsub);
                }

            }
        };
	    subscriptions.rm (pipe_, send_unsubscription, this);

	    dist.terminated (pipe_);
	}


    @Override
    protected int xrecv(Msg msg_, int flags_) {
        throw new UnsupportedOperationException();
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
