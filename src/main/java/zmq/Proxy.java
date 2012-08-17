/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2011 iMatix Corporation
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

public class Proxy extends Router {

    //  If true, we are in process of sending the reply. If false we are
    //  in process of receiving a request.
    private boolean sending_reply;

    //  If true, we are starting to receive a request. The beginning
    //  of the request is the backtrace stack.
    private boolean request_begins;
    
    
    public Proxy(Ctx parent_, int tid_, int sid_) {
        super(parent_, tid_, sid_);
        sending_reply = false;
        request_begins = true;
        
        options.send_identity = false;
        options.type = ZMQ.ZMQ_PROXY;
    }
    
    
    public static class ProxySession extends Router.RouterSession {

        private boolean message_begins;
        private boolean sent_identity;
        private final String address;
        
        public ProxySession(IOThread io_thread_, boolean connect_,
            SocketBase socket_, final Options options_,
            final Address addr_) {
  
            super(io_thread_, connect_, socket_, options_, addr_);
            message_begins = true;
            sent_identity = false;
            
            address = addr_.address();
        }
        
        @Override
        public Msg read() {
            
            Msg msg_ = null;
            
            if (pipe == null || (msg_ = pipe.read ()) == null ) {
                return null;
            }
            
            if ( msg_.has_more() && msg_.size () == 0) {
                //return null;
                msg_ = pipe.read ();
            }
            
            //incomplete_in = msg_.has_more();

            return msg_;

        }
        
        @Override
        public boolean write (Msg msg_)
        {
            //  generate First identity message 
            if (!sent_identity) {
                Msg identity = new Msg(address.length() +1);
                identity.put((byte)0);
                identity.put(address.getBytes(),1);
                if (!super.write(identity)) {
                    return false;
                }
                sent_identity = true;
            }
            // fake req
            //if (message_begins) {
                Msg bottom = new Msg();
                bottom.set_flags (Msg.more);

                if (!super.write(bottom)) {
                    return false;
                }
            //    message_begins = false;
            //}
            
            boolean success = super.write(msg_);
            return success;
        }
    }


}
