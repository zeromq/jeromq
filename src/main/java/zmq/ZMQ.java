package zmq;


public class ZMQ {

    /*  Context options  */
    public static final int ZMQ_IO_THREADS = 1;
    public static final int ZMQ_MAX_SOCKETS = 2;

    /*  Default for new contexts                                                  */
    public static final int ZMQ_IO_THREADS_DFLT = 1;
    public static final int ZMQ_MAX_SOCKETS_DFLT = 1024;
    

    /******************************************************************************/
    /*  0MQ socket definition.                                                    */
    /******************************************************************************/

    /*  Socket types.                                                             */
    public static final int ZMQ_PAIR = 0;
    public static final int ZMQ_PUB = 1;
    public static final int ZMQ_SUB = 2;
    public static final int ZMQ_REQ = 3;
    public static final int ZMQ_REP = 4;
    public static final int ZMQ_DEALER = 5;
    public static final int ZMQ_ROUTER = 6;
    public static final int ZMQ_PULL = 7;
    public static final int ZMQ_PUSH = 8;
    public static final int ZMQ_XPUB = 9;
    public static final int ZMQ_XSUB = 10;

    /*  Deprecated aliases                                                        */
    public static final int ZMQ_XREQ = ZMQ_DEALER;
    public static final int ZMQ_XREP = ZMQ_ROUTER;

    /*  Socket options.                                                           */
    public static final int ZMQ_AFFINITY = 4;
    public static final int ZMQ_IDENTITY = 5;
    public static final int ZMQ_SUBSCRIBE = 6;
    public static final int ZMQ_UNSUBSCRIBE = 7;
    public static final int ZMQ_RATE = 8;
    public static final int ZMQ_RECOVERY_IVL = 9;
    public static final int ZMQ_SNDBUF = 11;
    public static final int ZMQ_RCVBUF = 12;
    public static final int ZMQ_RCVMORE = 13;
    public static final int ZMQ_FD = 14;
    public static final int ZMQ_EVENTS = 15;
    public static final int ZMQ_TYPE = 16;
    public static final int ZMQ_LINGER = 17;
    public static final int ZMQ_RECONNECT_IVL = 18;
    public static final int ZMQ_BACKLOG = 19;
    public static final int ZMQ_RECONNECT_IVL_MAX = 21;
    public static final int ZMQ_MAXMSGSIZE = 22;
    public static final int ZMQ_SNDHWM = 23;
    public static final int ZMQ_RCVHWM = 24;
    public static final int ZMQ_MULTICAST_HOPS = 25;
    public static final int ZMQ_RCVTIMEO = 27;
    public static final int ZMQ_SNDTIMEO = 28;
    public static final int ZMQ_IPV4ONLY = 31;
    public static final int ZMQ_LAST_ENDPOINT = 32;
    public static final int ZMQ_ROUTER_BEHAVIOR = 33;
    public static final int ZMQ_TCP_KEEPALIVE = 34;
    public static final int ZMQ_TCP_KEEPALIVE_CNT = 35;
    public static final int ZMQ_TCP_KEEPALIVE_IDLE = 36;
    public static final int ZMQ_TCP_KEEPALIVE_INTVL = 37;
    public static final int ZMQ_TCP_ACCEPT_FILTER = 38;
    /*  Message options                                                           */
    
    public static final int ZMQ_MORE = 1;

    /*  Send/recv options.                                                        */
    public static final int ZMQ_DONTWAIT = 1;
    public static final int ZMQ_SNDMORE = 2;
    /******************************************************************************/
    /*  0MQ socket events and monitoring                                          */
    /******************************************************************************/

    /*  Socket transport events (tcp and ipc only)                                */
    public static final int ZMQ_EVENT_CONNECTED = 1;
    public static final int ZMQ_EVENT_CONNECT_DELAYED = 2;
    public static final int ZMQ_EVENT_CONNECT_RETRIED = 4;

    public static final int ZMQ_EVENT_LISTENING = 8;
    public static final int ZMQ_EVENT_BIND_FAILED = 16;

    public static final int ZMQ_EVENT_ACCEPTED = 32;
    public static final int ZMQ_EVENT_ACCEPT_FAILED = 64;

    public static final int ZMQ_EVENT_CLOSED = 128;
    public static final int ZMQ_EVENT_CLOSE_FAILED = 256;
    public static final int ZMQ_EVENT_DISCONNECTED = 512;
    
    public static Ctx zmq_ctx_new() {
        //  Create 0MQ context.
        Ctx ctx = new Ctx();
        //alloc_assert (ctx);
        return ctx;
    }

    public static Ctx zmq_init(int io_threads_) {
        if (io_threads_ >= 0) {
            Ctx ctx = zmq_ctx_new ();
            zmq_ctx_set (ctx, ZMQ_IO_THREADS, io_threads_);
            return ctx;
        }
        throw new IllegalArgumentException("io_threds must not be negative");
    }
    
    public static void zmq_ctx_set (Ctx ctx_, int option_, int optval_)
    {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
        ctx_.set (option_, optval_);
    }

    public static int zmq_ctx_get (Ctx ctx_, int option_)
    {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
        return ctx_.get (option_);
    }
    
    public static SocketBase zmq_socket (Ctx ctx_, int type_)
    {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
        SocketBase s = ctx_.create_socket (type_);
        return s;
    }
    
    public static int zmq_connect (SocketBase s_, String addr_)
    {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        int result = s_.connect (addr_);
        return result;
    }

    public static int zmq_close(SocketBase s_) {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        int result = s_.close ();
        return result;
    }

    public static int zmq_term(Ctx ctx_) {
        return zmq_ctx_destroy (ctx_);
    }

    private static int zmq_ctx_destroy(Ctx ctx_) {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
       
        int rc = ctx_.terminate ();
        return rc;
    }

    public static void zmq_setsockopt(SocketBase s_, int option_, int optval_) {

        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }

        s_.setsockopt (option_, optval_);

    }

    public static int zmq_bind(SocketBase s_, final String addr_) {
        
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        
        return s_.bind(addr_);
    }

}
