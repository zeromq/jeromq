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
/*#define ZMQ_XREQ ZMQ_DEALER
#define ZMQ_XREP ZMQ_ROUTER
*/
/*  Socket options.                                                           */
/*#define ZMQ_AFFINITY 4
#define ZMQ_IDENTITY 5
#define ZMQ_SUBSCRIBE 6
#define ZMQ_UNSUBSCRIBE 7
#define ZMQ_RATE 8
#define ZMQ_RECOVERY_IVL 9
#define ZMQ_SNDBUF 11
#define ZMQ_RCVBUF 12
#define ZMQ_RCVMORE 13
#define ZMQ_FD 14
#define ZMQ_EVENTS 15
#define ZMQ_TYPE 16
#define ZMQ_LINGER 17
#define ZMQ_RECONNECT_IVL 18
#define ZMQ_BACKLOG 19
#define ZMQ_RECONNECT_IVL_MAX 21
#define ZMQ_MAXMSGSIZE 22
#define ZMQ_SNDHWM 23
#define ZMQ_RCVHWM 24
#define ZMQ_MULTICAST_HOPS 25
#define ZMQ_RCVTIMEO 27
#define ZMQ_SNDTIMEO 28
#define ZMQ_IPV4ONLY 31
#define ZMQ_LAST_ENDPOINT 32
#define ZMQ_ROUTER_BEHAVIOR 33
#define ZMQ_TCP_KEEPALIVE 34
#define ZMQ_TCP_KEEPALIVE_CNT 35
#define ZMQ_TCP_KEEPALIVE_IDLE 36
#define ZMQ_TCP_KEEPALIVE_INTVL 37
#define ZMQ_TCP_ACCEPT_FILTER 38
*/
/*  Message options                                                           */
	/*
#define ZMQ_MORE 1
*/
/*  Send/recv options.                                                        */
/*#define ZMQ_DONTWAIT 1
#define ZMQ_SNDMORE 2
*/
/******************************************************************************/
/*  0MQ socket events and monitoring                                          */
/******************************************************************************/

/*  Socket transport events (tcp and ipc only)                                */
/*#define ZMQ_EVENT_CONNECTED 1
#define ZMQ_EVENT_CONNECT_DELAYED 2
#define ZMQ_EVENT_CONNECT_RETRIED 4

#define ZMQ_EVENT_LISTENING 8
#define ZMQ_EVENT_BIND_FAILED 16

#define ZMQ_EVENT_ACCEPTED 32
#define ZMQ_EVENT_ACCEPT_FAILED 64

#define ZMQ_EVENT_CLOSED 128
#define ZMQ_EVENT_CLOSE_FAILED 256
#define ZMQ_EVENT_DISCONNECTED 512
	 */
	
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
	    Errno.set(Errno.EINVAL);
	    return null;  
	}
	
	public static int zmq_ctx_set (Ctx ctx_, int option_, int optval_)
	{
	    if (ctx_ == null || !ctx_.check_tag ()) {
	        Errno.set(Errno.EFAULT);
	        return -1;
	    }
	    return ctx_.set (option_, optval_);
	}

	public static int zmq_ctx_get (Ctx ctx_, int option_)
	{
		if (ctx_ == null || !ctx_.check_tag ()) {
	        Errno.set(Errno.EFAULT);
	        return -1;
	    }
	    return ctx_.get (option_);
	}
	
	public static SocketBase zmq_socket (Ctx ctx_, int type_)
	{
		if (ctx_ == null || !ctx_.check_tag ()) {
	        Errno.set(Errno.EFAULT);
	        return null;
	    }
		SocketBase s = ctx_.create_socket (type_);
	    return s;
	}
	
	public static int zmq_connect (SocketBase s_, String addr_)
	{
	    if (s_ == null || !s_.check_tag ()) {
	    	Errno.set(Errno.ENOTSOCK);
	        return -1;
	    }
	    int result = s_.connect (addr_);
	    return result;
	}

    public static int zmq_close(SocketBase s_) {
        if (s_ == null || !s_.check_tag ()) {
            Errno.set(Errno.ENOTSOCK);
            return -1;
        }
        int result = s_.close ();
        return result;
    }

    public static int zmq_term(Ctx ctx_) {
        return zmq_ctx_destroy (ctx_);
    }

    private static int zmq_ctx_destroy(Ctx ctx_) {
        if (ctx_ == null || !ctx_.check_tag ()) {
            Errno.set(Errno.EFAULT);
            return -1;
        }
       
        int rc = ctx_.terminate ();
        return rc;
    }
}
