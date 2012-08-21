package org.jeromq;


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

        public Socket(Ctx ctx_, int type) {
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

            Msg msg = new Msg(data);
            
            return base.send(msg, flags);
        }

        public boolean bind(String addr) {
            return base.bind(addr);
        }

        public byte[] recv(int flags) {
            Msg msg = base.recv(flags);
            
            
            if (msg != null) {
                return msg.data();
            }
            
            return null;
        }
        
        public boolean hasReceiveMore() {
            return base.getsockopt (zmq.ZMQ.ZMQ_RCVMORE) == 1;
        }

        public boolean connect(String addr_) {
            return base.connect(addr_);
        }

    }


    public static Context context(int ioThreads) {
        return new Context(ioThreads);
    }


    public static String getVersionString() {
        return "" + zmq.ZMQ.ZMQ_VERSION_MAJOR + "." +
        		zmq.ZMQ.ZMQ_VERSION_MINOR + "." +
                zmq.ZMQ.ZMQ_VERSION_PATCH;
    }


    public static int getFullVersion() {
        return zmq.ZMQ.ZMQ_VERSION_MAJOR * 100000 + 
                zmq.ZMQ.ZMQ_VERSION_MINOR * 10000 + 
                zmq.ZMQ.ZMQ_VERSION_PATCH;
    }

}
