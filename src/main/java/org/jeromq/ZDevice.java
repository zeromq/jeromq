package org.jeromq;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.List;

import org.jeromq.ZMQ.Socket;

import zmq.Msg;
import zmq.PollItem;
import zmq.SocketBase;
import zmq.ZException;
import zmq.ZMQ;

public class ZDevice {

    public static interface IdentityFinder {
        public byte[] findIdentity(byte[] hint);
    }
    
    // router-router device
    // first byte of identity has hint for routing
    // worker must be dealer socket
    public static boolean addressDevice (Socket insocket,
            Socket outsocket, List<byte[]> identities)
    {

        SocketBase insocket_ = insocket.base();
        SocketBase outsocket_ = outsocket.base();
        boolean success = true;
        boolean sentAddress = false;
        boolean recvAddress = false;
        int rc;
        long more;
        Msg msg;
        byte[] target;
        int size = identities.size();
        PollItem items [] = new PollItem[2];
        
        if (insocket_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER ||
              outsocket_.getsockopt(ZMQ.ZMQ_TYPE) != ZMQ.ZMQ_ROUTER) {
            throw new IllegalArgumentException("Both router socket is required");
        }
        
        items[0] = new PollItem (insocket_, ZMQ.ZMQ_POLLIN );
        items[1] = new PollItem (outsocket_, ZMQ.ZMQ_POLLIN );
        
        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new ZException.IOException(e);
        }
        
        while (success) {
            //  Wait while there are either requests or replies to process.
            rc = ZMQ.zmq_poll (selector, items, -1);
            if (rc < 0)
                break;

            //  Process a request.
            if (items [0].isReadable()) {
                while (true) {
                    msg = insocket_.recv (0);
                    if (msg == null) {
                        success = false;
                        break;
                    }

                    if (!sentAddress) {
                        int hint = (int)msg.data()[0];
                        if (hint < 0) {
                            hint = (0xFF) & hint;
                        }
                        target = identities.get(hint % size);
                        // routing address
                        success = outsocket_.send (new Msg(target), ZMQ.ZMQ_SNDMORE);
                        if (!success)
                            break;
                        sentAddress = true;
                    }
                    
                    more = insocket_.getsockopt (ZMQ.ZMQ_RCVMORE);

                    success = outsocket_.send (msg, more > 0? ZMQ.ZMQ_SNDMORE: 0);
                    if (!success)
                        break;
                    if (more == 0) {
                        sentAddress = false;
                        break;
                    }
                }
            }
            //  Process a reply.
            if (success && items [1].isReadable()) {
                while (true) {
                    msg = outsocket_.recv (0);
                    if (msg == null) {
                        success = false;
                        break;
                    }
                    
                    if (!recvAddress) {
                        recvAddress = true;
                        continue;
                    }

                    more = outsocket_.getsockopt (ZMQ.ZMQ_RCVMORE);

                    success = insocket_.send (msg, more > 0? ZMQ.ZMQ_SNDMORE: 0);
                    if (!success)
                        break;
                    if (more == 0) {
                        recvAddress = false;
                        break;
                    }
                }
            }

        }
        
        try {
            selector.close();
        } catch (Exception e) {
        }
        
        return success;
        
    }
}
