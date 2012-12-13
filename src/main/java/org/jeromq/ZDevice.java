/*
    Copyright other contributors as noted in the AUTHORS file.
                
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
package org.jeromq;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.List;

import org.jeromq.ZMQ.Socket;

import zmq.Msg;
import zmq.PollItem;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class ZDevice {

    // router-router device
    // first byte of identity has hint for routing
    public static boolean addressDevice (Socket front,
            Socket backend, List <byte []> identities)
    {

        SocketBase front_ = front.base ();
        SocketBase backend_ = backend.base ();
        boolean success = true;
        boolean sentAddress = false;
        boolean recvAddress = false;
        int rc;
        boolean more;
        Msg msg;
        byte [] target;
        int size = identities.size ();
        PollItem items [] = new PollItem [2];
        
        if (front_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER ||
                backend_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER) {
            throw new IllegalArgumentException ("Both router socket is required");
        }
        
        items[0] = new PollItem (front_, ZMQ.ZMQ_POLLIN );
        items[1] = new PollItem (backend_, ZMQ.ZMQ_POLLIN );
        
        Selector selector;
        try {
            selector = Selector.open ();
        } catch (IOException e) {
            throw new ZError.IOException (e);
        }
        
        while (success) {
            //  Wait while there are either requests or replies to process.
            rc = ZMQ.zmq_poll (selector, items, -1);
            if (rc < 0)
                break;

            //  Process a request.
            if (items [0].isReadable ()) {
                while (true) {
                    msg = front_.recv (0);
                    if (msg == null) {
                        success = false;
                        break;
                    }

                    if (!sentAddress) {
                        int hint = (int) msg.data () [0];
                        if (hint < 0) {
                            hint = (0xFF) & hint;
                        }
                        target = identities.get (hint % size);
                        // routing address
                        success = backend_.send (new Msg (target), ZMQ.ZMQ_SNDMORE);
                        if (!success) {
                            System.out.println ("send 0 failed");

                            break;
                        }
                        sentAddress = true;
                    }
                    
                    more = msg.has_more ();

                    success = backend_.send (msg, more ? ZMQ.ZMQ_SNDMORE: 0);
                    if (!success) {
                        System.out.println ("send failed");
                        break;
                    }
                    
                    if (!more) {
                        sentAddress = false;
                        break;
                    }
                }
            }
            //  Process a reply.
            if (success && items [1].isReadable ()) {
                while (true) {
                    msg = backend_.recv (0);
                    if (msg == null) {
                        System.out.println ("recv failed");

                        success = false;
                        break;
                    }
                    
                    if (!recvAddress) {
                        // don't care worker's address
                        recvAddress = true;
                        continue;
                    }

                    more = msg.has_more ();

                    success = front_.send (msg, more ? ZMQ.ZMQ_SNDMORE: 0);
                    if (!success) {
                        System.out.println ("send 2 failed");

                        break;
                    }
                    if (!more) {
                        recvAddress = false;
                        break;
                    }
                }
            }

        }
        
        try {
            selector.close ();
        } catch (Exception e) {
        }
        
        return success;
        
    }
    
    // first byte of identity has hint for routing
    public static boolean loadBalanceDevice (Socket front,
            Socket backend, List <byte []> identities)
    {

        SocketBase front_ = front.base ();
        SocketBase backend_ = backend.base ();
        boolean success = true;
        boolean sentAddress = false;
        boolean recvAddress = false;
        int rc;
        boolean more;
        Msg msg;
        byte [] target;
        int size = identities.size ();
        int available = size;
        PollItem items [] = new PollItem [2];
        
        if (front_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER ||
                backend_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER) {
            throw new IllegalArgumentException ("Both router socket is required");
        }
        
        items[0] = new PollItem (front_, ZMQ.ZMQ_POLLIN );
        items[1] = new PollItem (backend_, ZMQ.ZMQ_POLLIN );
        
        Selector selector;
        try {
            selector = Selector.open ();
        } catch (IOException e) {
            throw new ZError.IOException (e);
        }
        
        while (success) {
            //  Wait while there are either requests or replies to process.
            if (available == 0)
                items [0].interestOps (0);
            else
                items [0].interestOps (ZMQ.ZMQ_POLLIN);
            
            rc = ZMQ.zmq_poll (selector, items, -1);
            if (rc < 0)
                break;

            //  Process a request.
            if (items [0].isReadable ()) {
                while (true) {
                    msg = front_.recv (0);
                    if (msg == null) {
                        success = false;
                        break;
                    }

                    if (!sentAddress) {
                        target = identities.get (--available);
                        // routing address
                        success = backend_.send (new Msg (target), ZMQ.ZMQ_SNDMORE);
                        if (!success) {
                            if (ZError.is (ZError.EHOSTUNREACH))
                                continue;
                            else
                                break;
                        }
                        sentAddress = true;
                    }
                    
                    more = msg.has_more ();

                    success = backend_.send (msg, more ? ZMQ.ZMQ_SNDMORE: 0);
                    if (!success)
                        break;
                    if (!more) {
                        sentAddress = false;
                        break;
                    }
                }
            }
            //  Process a reply.
            if (success && items [1].isReadable ()) {
                while (true) {
                    msg = backend_.recv (0);
                    if (msg == null) {
                        success = false;
                        break;
                    }
                    
                    if (!recvAddress) {
                        identities.set (available++, msg.data ());
                        recvAddress = true;
                        continue;
                    }

                    more = msg.has_more ();

                    success = front_.send (msg, more ? ZMQ.ZMQ_SNDMORE: 0);
                    
                    if (!success)
                        break;
                    if (!more) {
                        recvAddress = false;
                        break;
                    }
                }
            }

        }
        
        try {
            selector.close ();
        } catch (Exception e) {
        }
        
        return success;
        
    }
}
