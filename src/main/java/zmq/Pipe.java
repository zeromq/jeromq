package zmq;

public class Pipe extends ZObject {

    //  Underlying pipes for both directions.
    YPipe<Msg> inpipe;
    YPipe<Msg> outpipe;

    //  Can the pipe be read from / written to?
    boolean in_active;
    boolean out_active;

    //  High watermark for the outbound pipe.
    int hwm;

    //  Low watermark for the inbound pipe.
    int lwm;

    //  Number of messages read and written so far.
    long msgs_read;
    long msgs_written;

    //  Last received peer's msgs_read. The actual number in the peer
    //  can be higher at the moment.
    long peers_msgs_read;

    //  The pipe object on the other side of the pipepair.
    Pipe peer;

    //  Sink to send events to.
    IPipeEvents sink;
    
    //  State of the pipe endpoint. Active is common state before any
    //  termination begins. Delimited means that delimiter was read from
    //  pipe before term command was received. Pending means that term
    //  command was already received from the peer but there are still
    //  pending messages to read. Terminating means that all pending
    //  messages were already read and all we are waiting for is ack from
    //  the peer. Terminated means that 'terminate' was explicitly called
    //  by the user. Double_terminated means that user called 'terminate'
    //  and then we've got term command from the peer as well.
    enum State {
        active,
        delimited,
        pending,
        terminating,
        terminated,
        double_terminated
    } ;
    State state;

    //  If true, we receive all the pending inbound messages before
    //  terminating. If false, we terminate immediately when the peer
    //  asks us to.
    boolean delay;

    //  Identity of the writer. Used uniquely by the reader side.
    Blob identity;
    
	Pipe (ZObject parent_, YPipe<Msg> inpipe_, YPipe<Msg> outpipe_,
		      int inhwm_, int outhwm_, boolean delay_) {
		super(parent_);
		inpipe = inpipe_;
		outpipe = outpipe_;
		in_active = true;
		out_active = true;
		hwm = outhwm_;
		lwm = compute_lwm (inhwm_);
		msgs_read = 0;
		msgs_written = 0;
		peers_msgs_read = 0;
		peer = null ;
		sink = null ;
		state = State.active;
		delay = delay_;
	}
	
	public static int pipepair(ZObject[] parents_, Pipe[] pipes_, int[] hwms_,
			boolean[] delays_) {
		
	    //   Creates two pipe objects. These objects are connected by two ypipes,
	    //   each to pass messages in one direction.
	            
	    //Pipe.Upipe upipe1 = new Pipe.Upipe ();
	    //alloc_assert (upipe1);
		YPipe<Msg> upipe1 = new YPipe<Msg>(Config.message_pipe_granularity);
	    //pipe_t::upipe_t *upipe2 = new (std::nothrow) pipe_t::upipe_t ();
	    //alloc_assert (upipe2);
		YPipe<Msg> upipe2 = new YPipe<Msg>(Config.message_pipe_granularity);
	            
	    pipes_ [0] = new Pipe(parents_ [0], upipe1, upipe2,
	        hwms_ [1], hwms_ [0], delays_ [0]);
	    //alloc_assert (pipes_ [0]);
	    pipes_ [1] = new Pipe(parents_ [1], upipe2, upipe1,
	        hwms_ [0], hwms_ [1], delays_ [1]);
	    //alloc_assert (pipes_ [1]);
	            
	    pipes_ [0].set_peer (pipes_ [1]);
	    pipes_ [1].set_peer (pipes_ [0]);

		return 0;
	}
	
	int compute_lwm (int hwm_)
	{
	    //  Compute the low water mark. Following point should be taken
	    //  into consideration:
	    //
	    //  1. LWM has to be less than HWM.
	    //  2. LWM cannot be set to very low value (such as zero) as after filling
	    //     the queue it would start to refill only after all the messages are
	    //     read from it and thus unnecessarily hold the progress back.
	    //  3. LWM cannot be set to very high value (such as HWM-1) as it would
	    //     result in lock-step filling of the queue - if a single message is
	    //     read from a full queue, writer thread is resumed to write exactly one
	    //     message to the queue and go back to sleep immediately. This would
	    //     result in low performance.
	    //
	    //  Given the 3. it would be good to keep HWM and LWM as far apart as
	    //  possible to reduce the thread switching overhead to almost zero,
	    //  say HWM-LWM should be max_wm_delta.
	    //
	    //  That done, we still we have to account for the cases where
	    //  HWM < max_wm_delta thus driving LWM to negative numbers.
	    //  Let's make LWM 1/2 of HWM in such cases.
	    int result = (hwm_ > Config.max_wm_delta.getValue() * 2) ?
	        hwm_ - Config.max_wm_delta.getValue() : (hwm_ + 1) / 2;

	    return result;
	}
	
	void set_peer (Pipe peer_)
	{
	    //  Peer can be set once only.
	    assert (peer != null);
	    peer = peer_;
	}

	public void set_event_sink(SocketBase sink_) {
	    // Sink can be set once only.
	    assert (sink != null);
	    sink = sink_;
	}
	
    Msg read()
	{
	    Msg msg_;
	    if (!in_active || (state != State.active && state != State.pending))
	        return null;

	    if ((msg_ = inpipe.read ()) == null) {
	        in_active = false;
	        return null;
	    }

	    //  If delimiter was read, start termination process of the pipe.
	    if (msg_.is_delimiter ()) {
	        delimit ();
	        return null;
	    }

	    if ((msg_.flags () & Msg.more) == 0)
	        msgs_read++;

	    if (lwm > 0 && msgs_read % lwm == 0)
	        send_activate_write (peer, msgs_read);

	    return msg_;
	}
    
    boolean write (Msg msg_)
    {
        if (!check_write ())
            return false;

        boolean more = (msg_.flags () & Msg.more) > 0 ? true : false;
        outpipe.write (msg_, more);
        if (!more)
            msgs_written++;

        return true;
    }

    boolean check_write ()
    {
        if (!out_active || state != State.active)
            return false;

        boolean full = hwm > 0 && msgs_written - peers_msgs_read == (long) (hwm);

        if (full) {
            out_active = false;
            return false;
        }

        return true;
    }

	
	void delimit ()
	{
	    if (state == State.active) {
	        state = State.delimited;
	        return;
	    }

	    if (state == State.pending) {
	        outpipe = null;
	        send_pipe_term_ack (peer);
	        state = State.terminating;
	        return;
	    }

	    //  Delimiter in any other state is invalid.
	    assert (false);
	}

    void terminate (boolean delay_)
	{
	    //  Overload the value specified at pipe creation.
	    delay = delay_;

	    //  If terminate was already called, we can ignore the duplicit invocation.
	    if (state == State.terminated || state == State.double_terminated)
	        return;

	    //  If the pipe is in the final phase of async termination, it's going to
	    //  closed anyway. No need to do anything special here.
	    else if (state == State.terminating)
	        return;

	    //  The simple sync termination case. Ask the peer to terminate and wait
	    //  for the ack.
	    else if (state == State.active) {
	        send_pipe_term (peer);
	        state = State.terminated;
	    }

	    //  There are still pending messages available, but the user calls
	    //  'terminate'. We can act as if all the pending messages were read.
	    else if (state == State.pending && !delay) {
	        outpipe = null;
	        send_pipe_term_ack (peer);
	        state = State.terminating;
	    }

	    //  If there are pending messages still availabe, do nothing.
	    else if (state == State.pending) {
	    }

	    //  We've already got delimiter, but not term command yet. We can ignore
	    //  the delimiter and ack synchronously terminate as if we were in
	    //  active state.
	    else if (state == State.delimited) {
	        send_pipe_term (peer);
	        state = State.terminated;
	    }

	    //  There are no other states.
	    else
	        assert (false);

	    //  Stop outbound flow of messages.
	    out_active = false;

	    if (outpipe != null) {

	        //  Drop any unfinished outbound messages.
	        rollback ();

	        //  Write the delimiter into the pipe. Note that watermarks are not
	        //  checked; thus the delimiter can be written even when the pipe is full.
	        Msg msg = new Msg();
	        msg.init_delimiter ();
	        outpipe.write (msg, false);
	        flush ();
	    }
	}


	void rollback ()
	{
	    //  Remove incomplete message from the outbound pipe.
	    Msg msg;
	    if (outpipe!= null) {
	        while ((msg = outpipe.unwrite ()) != null) {
	            assert ((msg.flags () & Msg.more) > 0);
	            int rc = msg.close ();
	            Errno.errno_assert (rc == 0);
	        }
	    }
	}
	
	void flush ()
	{
	    //  If terminate() was already called do nothing.
	    if (state == State.terminated && state == State.double_terminated)
	        return;

	    //  The peer does not exist anymore at this point.
	    if (state == State.terminating)
	        return;

	    if (outpipe != null && !outpipe.flush ())
	        send_activate_read (peer);
	}

    public void set_event_sink(IPipeEvents sink_) {
        assert (sink == null);
        sink = sink_;
    }


}
