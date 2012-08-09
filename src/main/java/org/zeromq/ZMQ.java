package org.zeromq;


import java.nio.ByteBuffer;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;

public class ZMQ {
    
    public static final int SNDMORE = zmq.ZMQ.ZMQ_SNDMORE;
    
    public static final int PAIR = zmq.ZMQ.ZMQ_PAIR;
    public static final int PUB = zmq.ZMQ.ZMQ_PUB;
    public static final int SUB = zmq.ZMQ.ZMQ_SUB;
    public static final int REQ = zmq.ZMQ.ZMQ_REQ;
    public static final int REP = zmq.ZMQ.ZMQ_REP;
    public static final int DEALER = zmq.ZMQ.ZMQ_DEALER;
    public static final int ROUTER = zmq.ZMQ.ZMQ_ROUTER;
    public static final int PULL = zmq.ZMQ.ZMQ_PULL;
    public static final int PUSH = zmq.ZMQ.ZMQ_PUSH;
    public static final int XPUB = zmq.ZMQ.ZMQ_XPUB;
    public static final int XSUB = zmq.ZMQ.ZMQ_XSUB;
    public static final int PLAIN = zmq.ZMQ.ZMQ_PLAIN;

    
    public static class Context {

        private Ctx ctx;
        
        public Context(int ioThreads) {
            ctx = zmq.ZMQ.zmq_init(ioThreads);
        }

        public void term() {
            ctx.terminate();
        }

        public Socket socket(int type) {
            return new Socket(ctx, type);
        }

    }
    
    public static class Socket {

        private Ctx ctx;
        private SocketBase base;
        private boolean hasMore;

        public Socket(Ctx ctx_, int type) {
            hasMore = false;
            ctx = ctx_;
            base = ctx.create_socket(type);
        }

        public void setLinger(int linger) {
            base.setsockopt(zmq.ZMQ.ZMQ_LINGER, linger);
        }

        public void close() {
            base.close();
            
        }

        public boolean send(byte[] data, int flags) {

            Msg msg = new Msg(data.length);
            msg.put(data);
            
            return base.send(msg, flags);
        }

        public boolean send(ByteBuffer data, int flags) {

            Msg msg = new Msg(data.remaining());
            msg.put(data);
            
            return base.send(msg, flags);
        }
        
        public boolean bind(String addr) {
            return base.bind(addr);
        }

        public ByteBuffer recv(int flags) {
            Msg msg = base.recv(flags);
            
            
            if (msg != null) {
                hasMore = msg.has_more();
                return msg.data();
            }
            
            hasMore = false;
            return null;
        }
        
        public boolean hasReceiveMore() {
            return hasMore;
        }

    }


    public static Context context(int ioThreads) {
        return new Context(ioThreads);
    }

}
