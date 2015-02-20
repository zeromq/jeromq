/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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

public class Req extends Dealer
{
    //  If true, request was already sent and reply wasn't received yet or
    //  was raceived partially.
    private boolean receivingReply;

    //  If true, we are starting to send/recv a message. The first part
    //  of the message must be empty message part (backtrace stack bottom).
    private boolean messageBegins;

    public Req(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        receivingReply = false;
        messageBegins = true;
        options.type = ZMQ.ZMQ_REQ;
    }

    @Override
    public boolean xsend(Msg msg)
    {
        //  If we've sent a request and we still haven't got the reply,
        //  we can't send another request.
        if (receivingReply) {
            errno.set(ZError.EFSM);
            return false;
        }

        //  First part of the request is the request identity.
        if (messageBegins) {
            Msg bottom = new Msg();
            bottom.setFlags(Msg.MORE);
            boolean rc = super.xsend(bottom);
            if (!rc) {
                return rc;
            }
            messageBegins = false;
        }

        boolean more = msg.hasMore();

        boolean rc = super.xsend(msg);
        if (!rc) {
            return rc;
        }

        //  If the request was fully sent, flip the FSM into reply-receiving state.
        if (!more) {
            receivingReply = true;
            messageBegins = true;
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        //  If request wasn't send, we can't wait for reply.
        if (!receivingReply) {
            errno.set(ZError.EFSM);
            return null;
        }
        Msg msg = null;
        //  First part of the reply should be the original request ID.
        if (messageBegins) {
            msg = super.xrecv();
            if (msg == null) {
                return null;
            }

            // TODO: This should also close the connection with the peer!
            if (!msg.hasMore() || msg.size() != 0) {
                while (true) {
                    msg = super.xrecv();
                    assert (msg != null);
                    if (!msg.hasMore()) {
                        break;
                    }
                }
                errno.set(ZError.EAGAIN);
                return null;
            }

            messageBegins = false;
        }

        msg = super.xrecv();
        if (msg == null) {
            return null;
        }

        //  If the reply is fully received, flip the FSM into request-sending state.
        if (!msg.hasMore()) {
            receivingReply = false;
            messageBegins = true;
        }

        return msg;
    }

    @Override
    public boolean xhasIn()
    {
        //  TODO: Duplicates should be removed here.

        return receivingReply && super.xhasIn();
    }

    @Override
    public boolean xhasOut()
    {
        return !receivingReply && super.xhasOut();
    }

    public static class ReqSession extends Dealer.DealerSession
    {
        enum State {
            IDENTITY,
            BOTTOM,
            BODY
        };

        private State state;

        public ReqSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);

            state = State.IDENTITY;
        }

        @Override
        public int pushMsg(Msg msg)
        {
            switch (state) {
            case BOTTOM:
                if (msg.hasMore() && msg.size() == 0) {
                    state = State.BODY;
                    return super.pushMsg(msg);
                }
                break;
            case BODY:
                if (msg.hasMore()) {
                    return super.pushMsg(msg);
                }
                if (msg.flags() == 0) {
                    state = State.BOTTOM;
                    return super.pushMsg(msg);
                }
                break;
            case IDENTITY:
                if (msg.flags() == 0) {
                    state = State.BOTTOM;
                    return super.pushMsg(msg);
                }
                break;
            }
            socket.errno.set(ZError.EFAULT);
            return -1;
        }

        public void reset()
        {
            super.reset();
            state = State.IDENTITY;
        }
    }
}
