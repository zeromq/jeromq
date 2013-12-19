/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
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

public class Rep extends Router {

    public static class RepSession extends Router.RouterSession {
        public RepSession(IOThread io_thread_, boolean connect_,
            SocketBase socket_, final Options options_,
            final Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }
    }
    //  If true, we are in process of sending the reply. If false we are
    //  in process of receiving a request.
    private boolean sending_reply;

    //  If true, we are starting to receive a request. The beginning
    //  of the request is the backtrace stack.
    private boolean request_begins;
    
    
    public Rep(Ctx parent_, int tid_, int sid_) {
        super(parent_, tid_, sid_);
        sending_reply = false;
        request_begins = true;
        
        options.type = ZMQ.ZMQ_REP;
    }
    
    @Override
    protected boolean xsend(Msg msg_)
    {
        //  If we are in the middle of receiving a request, we cannot send reply.
        if (!sending_reply) {
            throw new IllegalStateException("Cannot send another reply");
        }

        boolean more = msg_.hasMore();

        //  Push message to the reply pipe.
        boolean rc = super.xsend(msg_);
        if (!rc)
            return rc;

        //  If the reply is complete flip the FSM back to request receiving state.
        if (!more)
            sending_reply = false;

        return true;
    }
    
    @Override
    protected Msg xrecv()
    {
        //  If we are in middle of sending a reply, we cannot receive next request.
        if (sending_reply) {
            throw new IllegalStateException("Cannot receive another request");
        }

        Msg msg_ = null;
        //  First thing to do when receiving a request is to copy all the labels
        //  to the reply pipe.
        if (request_begins) {
            while (true) {
                msg_ = super.xrecv();
                if (msg_ == null)
                    return null;
                
                if (msg_.hasMore()) {
                    //  Empty message part delimits the traceback stack.
                    boolean bottom = (msg_.size() == 0);
                    
                    //  Push it to the reply pipe.
                    boolean rc = super.xsend(msg_);
                    assert (rc);
                    if (bottom)
                        break;
                } else {
                    //  If the traceback stack is malformed, discard anything
                    //  already sent to pipe (we're at end of invalid message).
                    super.rollback();
                }
            }
            request_begins = false;
        }

        //  Get next message part to return to the user.
        msg_ = super.xrecv();
        if (msg_ == null)
           return null;

        //  If whole request is read, flip the FSM to reply-sending state.
        if (!msg_.hasMore()) {
            sending_reply = true;
            request_begins = true;
        }

        return msg_;
    }

    @Override
    protected boolean xhas_in ()
    {
        if (sending_reply)
            return false;

        return super.xhas_in ();
    }
    
    @Override
    protected boolean xhas_out ()
    {
        if (!sending_reply)
            return false;

        return super.xhas_out ();
    }



}
