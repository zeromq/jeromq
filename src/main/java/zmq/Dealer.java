/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2011 VMware, Inc.
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

public class Dealer extends SocketBase {
    
    public static class DealerSession extends SessionBase {
        public DealerSession(IOThread io_thread_, boolean connect_,
            SocketBase socket_, final Options options_,
            final Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }
    }
    
    //  Messages are fair-queued from inbound pipes. And load-balanced to
    //  the outbound pipes.
    private final FQ fq;
    private final LB lb;

    //  Have we prefetched a message.
    private boolean prefetched;
    
    private Msg prefetched_msg;

    //  Holds the prefetched message.
    public Dealer(Ctx parent_, int tid_, int sid_) {
        super(parent_, tid_, sid_);
        
        prefetched = false;
        options.type = ZMQ.ZMQ_DEALER;
        
        fq = new FQ();
        lb = new LB();
        //  TODO: Uncomment the following line when DEALER will become true DEALER
        //  rather than generic dealer socket.
        //  If the socket is closing we can drop all the outbound requests. There'll
        //  be noone to receive the replies anyway.
        //  options.delay_on_close = false;
            
        options.recv_identity = true;
    }


    @Override
    protected void xattach_pipe(Pipe pipe_, boolean icanhasall_) {
        assert (pipe_ != null);
        fq.attach (pipe_);
        lb.attach (pipe_);
    }
    
    @Override
    protected boolean xsend(Msg msg_)
    {
        return lb.send(msg_, errno);
    }

    @Override
    protected Msg xrecv()
    {
        return xxrecv();
    }

    private Msg xxrecv()
    {
        Msg msg_ = null;
        //  If there is a prefetched message, return it.
        if (prefetched) {
            msg_ = prefetched_msg;
            prefetched = false;
            prefetched_msg = null;
            return msg_;
        }

        //  DEALER socket doesn't use identities. We can safely drop it and 
        while (true) {
            msg_ = fq.recv(errno);
            if (msg_ == null)
                return null;
            if ((msg_.flags() & Msg.IDENTITY) == 0)
                break;
        }
        return msg_;
    }

    @Override
    protected boolean xhas_in ()
    {
        //  We may already have a message pre-fetched.
        if (prefetched)
            return true;

        //  Try to read the next message to the pre-fetch buffer.
        prefetched_msg = xxrecv();
        if (prefetched_msg == null)
            return false;
        prefetched = true;
        return true;
    }

    @Override
    protected boolean xhas_out ()
    {
        return lb.has_out ();
    }

    @Override
    protected void xread_activated (Pipe pipe_)
    {
        fq.activated (pipe_);
    }

    @Override
    protected void xwrite_activated (Pipe pipe_)
    {
        lb.activated (pipe_);
    }


    @Override
    protected void xterminated(Pipe pipe_) {
        fq.terminated (pipe_);
        lb.terminated (pipe_);
    }

}
