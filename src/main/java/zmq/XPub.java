/*      
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2011 VMware, Inc.
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file
        
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
    private final Mtrie subscriptions;

    //  Distributor of messages holding the list of outbound pipes.
    private final Dist dist;

    // If true, send all subscription messages upstream, not just
    // unique ones
    boolean verbose;

    //  True if we are in the middle of sending a multi-part message.
    private boolean more;

    //  List of pending (un)subscriptions, ie. those that were already
    //  applied to the trie, but not yet received by the user.
    private final Deque<Blob> pending_data;
    private final Deque<Integer> pending_flags;
    
    private static Mtrie.IMtrieHandler mark_as_matching;
    private static Mtrie.IMtrieHandler send_unsubscription;
    
    static {
        mark_as_matching = new Mtrie.IMtrieHandler() {

            @Override
            public void invoke(Pipe pipe_, byte[] data, int size, Object arg_) {
                XPub self = (XPub) arg_;
                self.dist.match (pipe_);
            }
            
        };
        
        send_unsubscription = new Mtrie.IMtrieHandler() {
            
            @Override
            public void invoke(Pipe pipe_, byte[] data_, int size, Object arg_) {
                XPub self = (XPub) arg_;

                if (self.options.type != ZMQ.ZMQ_PUB) {
                    //  Place the unsubscription to the queue of pending (un)sunscriptions
                    //  to be retrived by the user later on.
                    byte[] unsub = new byte[size + 1];
                    unsub[0] = 0;
                    System.arraycopy(data_, 0, unsub, 1, size);
                    self.pending_data.add (Blob.createBlob(unsub, false));
                    self.pending_flags.add (0);
                }
            }
        };
    }
    
    public XPub(Ctx parent_, int tid_, int sid_) {
        super (parent_, tid_, sid_);
        
        options.type = ZMQ.ZMQ_XPUB;
        verbose = false;
        more = false;
        
        subscriptions = new Mtrie();
        dist = new Dist();
        pending_data = new ArrayDeque<Blob>();
        pending_flags = new ArrayDeque<Integer>();
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
        while ((sub = pipe_.read()) != null) {

            //  Apply the subscription to the trie
            byte[] data = sub.data();
            int size = sub.size ();
            if (size > 0 && (data[0] == 0 || data[0] == 1)) {
                boolean unique;
                if (data[0] == 0)
                    unique = subscriptions.rm (data , 1, pipe_);
                else
                    unique = subscriptions.add (data , 1, pipe_);

                //  If the subscription is not a duplicate, store it so that it can be
                //  passed to used on next recv call. (Unsubscribe is not verbose.)
                if (options.type == ZMQ.ZMQ_XPUB && (unique || (data[0] == 1 && verbose))) {
                    pending_data.add (Blob.createBlob(data, true));
                    pending_flags.add(0);
                }
            } else {
                //  Process user message coming upstream from xsub socket
                pending_data.add (Blob.createBlob(data, true));
                pending_flags.add(sub.flags());
            }
        }
    }
    
    @Override
    protected void xwrite_activated (Pipe pipe_)
    {
        dist.activated (pipe_);
    }

    @Override
    public boolean xsetsockopt (int option_, Object optval_)
    {
        if (option_ != ZMQ.ZMQ_XPUB_VERBOSE) {
            return false;
        }
        verbose = (Integer) optval_ == 1;

        return true;
    }

    @Override
    protected void xterminated (Pipe pipe_)
    {
        //  Remove the pipe from the trie. If there are topics that nobody
        //  is interested in anymore, send corresponding unsubscriptions
        //  upstream.
        
        
        subscriptions.rm (pipe_, send_unsubscription, this);

        dist.terminated (pipe_);
    }



    @Override
    protected boolean xsend(Msg msg_) {
        boolean msg_more = msg_.hasMore(); 

        //  For the first part of multi-part message, find the matching pipes.
        if (!more)
            subscriptions.match (msg_.data (), msg_.size(),
                mark_as_matching, this);

        //  Send the message to all the pipes that were marked as matching
        //  in the previous step.
        boolean rc = dist.send_to_matching (msg_);
        if (!rc)
            return false;

        //  If we are at the end of multi-part message we can mark all the pipes
        //  as non-matching.
        if (!msg_more)
            dist.unmatch ();

        more = msg_more;
        return true;
    }
    
    @Override
    protected boolean xhas_out() {
        return dist.has_out ();
    }
    
    @Override
    protected Msg xrecv() {
        //  If there is at least one 
        if (pending_data.isEmpty ()) {
            errno.set(ZError.EAGAIN);
            return null;
        }

        Blob first = pending_data.pollFirst();
        Msg msg = new Msg(first.data());
        int flags = pending_flags.pollFirst();
        msg.setFlags(flags);
        return msg;
    }
    
    @Override
    protected boolean xhas_in() {
        return !pending_data.isEmpty ();
    }
}
