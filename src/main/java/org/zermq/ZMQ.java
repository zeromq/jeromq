package org.zermq;


import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;

public class ZMQ {
    
    public static final int SNDMORE = zmq.ZMQ.ZMQ_SNDMORE;

    
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

        public void send(byte[] data, int flags) {

            Msg msg = new Msg(data.length);
            msg.put(data);
            
            base.send(msg, flags);
        }

        public byte[] recv(int flags) {
            Msg msg = base.recv(flags);
            
            
            if (msg != null) {
                hasMore = msg.has_more();
                return msg.data().array();
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
