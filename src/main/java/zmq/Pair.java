/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
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

public class Pair extends SocketBase {
    
    public static class PairSession extends SessionBase {
        public PairSession(IOThread io_thread_, boolean connect_,
            SocketBase socket_, final Options options_,
            final Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }
    }

    private Pipe pipe;
    
	Pair(Ctx parent_, int tid_, int sid_) {
		super(parent_, tid_, sid_);
		options.type = ZMQ.ZMQ_PAIR;
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
        //  There's just one pipe. No lists of active and inactive pipes.
        //  There's nothing to do here.
    }

    
    @Override
    protected void xwrite_activated (Pipe pipe_)
    {
        //  There's just one pipe. No lists of active and inactive pipes.
        //  There's nothing to do here.
    }
    
    @Override
    protected boolean xsend(Msg msg_)
    {
        if (pipe == null || !pipe.write (msg_)) {
            errno.set(ZError.EAGAIN);
            return false;
        }

        if ((msg_.flags() & ZMQ.ZMQ_SNDMORE) == 0)
            pipe.flush ();

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        //  Deallocate old content of the message.
        Msg msg_ = null;
        if (pipe == null || (msg_ = pipe.read()) == null) {
            //  Initialise the output parameter to be a 0-byte message.
            errno.set(ZError.EAGAIN);
            return null;
        }
        return msg_;
    }


    @Override
    protected boolean xhas_in() {
        if (pipe == null)
            return false;

        return pipe.check_read ();
    }
    
    @Override
    protected boolean xhas_out ()
    {
        if (pipe == null)
            return false;

        return pipe.check_write ();
    }

}
