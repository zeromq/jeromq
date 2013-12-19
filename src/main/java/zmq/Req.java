/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
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

public class Req extends Dealer {

    
    //  If true, request was already sent and reply wasn't received yet or
    //  was raceived partially.
    private boolean receiving_reply;

    //  If true, we are starting to send/recv a message. The first part
    //  of the message must be empty message part (backtrace stack bottom).
    private boolean message_begins;
    
    
    public Req(Ctx parent_, int tid_, int sid_) {
        super(parent_, tid_, sid_);
        
        receiving_reply = false;
        message_begins = true;
        options.type = ZMQ.ZMQ_REQ;
    }
    
    @Override
    public boolean xsend(Msg msg_)
    {
        //  If we've sent a request and we still haven't got the reply,
        //  we can't send another request.
        if (receiving_reply) {
            throw new IllegalStateException("Cannot send another request");
        }

        //  First part of the request is the request identity.
        if (message_begins) {
            Msg bottom = new Msg();
            bottom.setFlags(Msg.MORE);
            boolean rc = super.xsend(bottom);
            if (!rc)
                return rc;
            message_begins = false;
        }

        boolean more = msg_.hasMore();

        boolean rc = super.xsend(msg_);
        if (!rc)
            return rc;

        //  If the request was fully sent, flip the FSM into reply-receiving state.
        if (!more) {
            receiving_reply = true;
            message_begins = true;
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        //  If request wasn't send, we can't wait for reply.
        if (!receiving_reply) {
            throw new IllegalStateException("Cannot wait before send");
        }
        Msg msg_ = null;
        //  First part of the reply should be the original request ID.
        if (message_begins) {
            msg_ = super.xrecv();
            if (msg_ == null)
                return null;

            // TODO: This should also close the connection with the peer!
            if ( !msg_.hasMore() || msg_.size() != 0) {
                while (true) {
                    msg_ = super.xrecv();
                    assert (msg_ != null);
                    if (!msg_.hasMore())
                        break;
                }
                errno.set(ZError.EAGAIN);
                return null;
            }

            message_begins = false;
        }

        msg_ = super.xrecv();
        if (msg_ == null)
            return null;

        //  If the reply is fully received, flip the FSM into request-sending state.
        if (!msg_.hasMore()) {
            receiving_reply = false;
            message_begins = true;
        }

        return msg_;
    }
    
    @Override
    public boolean xhas_in ()
    {
        //  TODO: Duplicates should be removed here.

        if (!receiving_reply)
            return false;

        return super.xhas_in ();
    }

    @Override
    public boolean xhas_out ()
    {
        if (receiving_reply)
            return false;

        return super.xhas_out ();
    }

    
    public static class ReqSession extends Dealer.DealerSession {
        
        
        enum State {
            IDENTITY,
            BOTTOM,
            BODY
        };

        private State state;
        
        public ReqSession(IOThread io_thread_, boolean connect_,
            SocketBase socket_, final Options options_,
            final Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
            
            state = State.IDENTITY;
        }
        
        @Override
        public int push_msg (Msg msg_)
        {
            switch (state) {
            case BOTTOM:
                if (msg_.flags () == Msg.MORE && msg_.size () == 0) {
                    state = State.BODY;
                    return super.push_msg (msg_);
                }
                break;
            case BODY:
                if (msg_.flags () == Msg.MORE)
                    return super.push_msg (msg_);
                if (msg_.flags () == 0) {
                    state = State.BOTTOM;
                    return super.push_msg (msg_);
                }
                break;
            case IDENTITY:
                if (msg_.flags () == 0) {
                    state = State.BOTTOM;
                    return super.push_msg (msg_);
                }
                break;
            }
            
            throw new IllegalStateException(state.toString());
        }
        
        public void reset ()
        {
            super.reset ();
            state = State.IDENTITY;
        }
    }
}
