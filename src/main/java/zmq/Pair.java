package zmq;

public class Pair extends SocketBase {

    Pipe pipe;
    
	Pair(Ctx parent_, int tid_, int sid_) {
		super(parent_, tid_, sid_);
	}

	@Override
	protected void xattach_pipe (Pipe pipe_, boolean icanhasall_)
	{     
	    assert (pipe_ != null);
	          
	    //  ZMQ_PAIR socket can only be connected to a single peer.
	    //  The socket rejects any further connection requests.
	    if (pipe == null)
	        pipe = pipe_;
	    else
	        pipe_.terminate (false);
	}
	
	@Override
	protected void xterminated (Pipe pipe_) {
	    if (pipe_ == pipe)
	        pipe = null;
	}

    @Override
    protected void xread_activated(Pipe pipe_) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhas_in() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    protected void xwrite_activated (Pipe pipe_)
    {
        //  There's just one pipe. No lists of active and inactive pipes.
        //  There's nothing to do here.
    }

}
