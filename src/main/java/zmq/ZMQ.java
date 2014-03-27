/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.HashMap;


public class ZMQ {

    /******************************************************************************/
    /*  0MQ versioning support.                                                   */
    /******************************************************************************/

    /*  Version macros for compile-time API version detection                     */
    public static final int ZMQ_VERSION_MAJOR = 3;
    public static final int ZMQ_VERSION_MINOR = 2;
    public static final int ZMQ_VERSION_PATCH = 2;
    
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
    @Deprecated
    public static final int ZMQ_XREQ = ZMQ_DEALER;
    @Deprecated
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
    public static final int ZMQ_ROUTER_MANDATORY = 33;
    public static final int ZMQ_TCP_KEEPALIVE = 34;
    public static final int ZMQ_TCP_KEEPALIVE_CNT = 35;
    public static final int ZMQ_TCP_KEEPALIVE_IDLE = 36;
    public static final int ZMQ_TCP_KEEPALIVE_INTVL = 37;
    public static final int ZMQ_TCP_ACCEPT_FILTER = 38;
    public static final int ZMQ_DELAY_ATTACH_ON_CONNECT = 39;
    public static final int ZMQ_XPUB_VERBOSE = 40;
    
    /* Custom options */
    public static final int ZMQ_ENCODER = 1001;
    public static final int ZMQ_DECODER = 1002;
    
    /*  Message options                                                           */
    
    public static final int ZMQ_MORE = 1;

    /*  Send/recv options.                                                        */
    public static final int ZMQ_DONTWAIT = 1;
    public static final int ZMQ_SNDMORE = 2;
    
    /*  Deprecated aliases                                                        */
    public static final int ZMQ_NOBLOCK = ZMQ_DONTWAIT;
    public static final int ZMQ_FAIL_UNROUTABLE = ZMQ_ROUTER_MANDATORY;
    public static final int ZMQ_ROUTER_BEHAVIOR = ZMQ_ROUTER_MANDATORY;
    
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
    public static final int ZMQ_EVENT_MONITOR_STOPPED = 1024;

    public static final int ZMQ_EVENT_ALL = ZMQ_EVENT_CONNECTED | ZMQ_EVENT_CONNECT_DELAYED |
                ZMQ_EVENT_CONNECT_RETRIED | ZMQ_EVENT_LISTENING |
                ZMQ_EVENT_BIND_FAILED | ZMQ_EVENT_ACCEPTED |
                ZMQ_EVENT_ACCEPT_FAILED | ZMQ_EVENT_CLOSED |
                ZMQ_EVENT_CLOSE_FAILED | ZMQ_EVENT_DISCONNECTED | ZMQ_EVENT_MONITOR_STOPPED;
    
    public static final int ZMQ_POLLIN = 1;
    public static final int ZMQ_POLLOUT = 2;
    public static final int ZMQ_POLLERR = 4;
    
    public static final int ZMQ_STREAMER = 1;
    public static final int ZMQ_FORWARDER = 2;
    public static final int ZMQ_QUEUE = 3;
    
    public static final byte[] MESSAGE_SEPARATOR = new byte[0];

    public static final byte[] SUBSCRIPTION_ALL = new byte[0];

    public static Charset CHARSET = Charset.forName("UTF-8");

    public static class Event
    {
        private static final int VALUE_INTEGER = 1;
        private static final int VALUE_CHANNEL = 2;
        
        public final int event;
        public final String addr;
        public final Object arg;
        private final int flag;

        public Event (int event, String addr, Object arg) 
        {
            this.event = event;
            this.addr = addr;
            this.arg = arg;
            if (arg instanceof Integer)
                flag = VALUE_INTEGER;
            else if (arg instanceof SelectableChannel)
                flag = VALUE_CHANNEL;
            else
                flag = 0;
        }
        
        public boolean write (SocketBase s) 
        {
            int size = 4 + 1 + addr.length () + 1; // event + len(addr) + addr + flag
            if (flag == VALUE_INTEGER)
                size += 4;
            
            ByteBuffer buffer = ByteBuffer.allocate (size).order(ByteOrder.BIG_ENDIAN);
            buffer.putInt (event);
            buffer.put ((byte) addr.length());
            buffer.put (addr.getBytes(CHARSET));
            buffer.put ((byte) flag);
            if (flag == VALUE_INTEGER)
                buffer.putInt ((Integer)arg);
            buffer.flip();

            Msg msg = new Msg (buffer);
            return s.send (msg, 0);
        }
        
        public static Event read (SocketBase s, int flags) 
        {
            Msg msg = s.recv (flags);
            if (msg == null)
                return null;
            
            ByteBuffer buffer = msg.buf ();
            
            int event = buffer.getInt ();
            int len = buffer.get ();
            byte [] addr = new byte [len];
            buffer.get (addr);
            int flag = buffer.get ();
            Object arg = null;
            
            if (flag == VALUE_INTEGER)
                arg = buffer.getInt ();
            
            return new Event (event, new String (addr, CHARSET), arg);
        }
        
        public static Event read (SocketBase s) {
            return read(s, 0);
        }
    }
    //  New context API
    public static Ctx zmq_ctx_new() {
        //  Create 0MQ context.
        Ctx ctx = new Ctx();
        return ctx;
    }
    
    private static void zmq_ctx_destroy(Ctx ctx_) {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
       
        ctx_.terminate ();
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
    

    //  Stable/legacy context API
    public static Ctx zmq_init(int io_threads_) {
        if (io_threads_ >= 0) {
            Ctx ctx = zmq_ctx_new ();
            zmq_ctx_set (ctx, ZMQ_IO_THREADS, io_threads_);
            return ctx;
        }
        throw new IllegalArgumentException("io_threds must not be negative");
    }
    
    public static void zmq_term(Ctx ctx_) {
        zmq_ctx_destroy (ctx_);
    }
    
    // Sockets
    public static SocketBase zmq_socket (Ctx ctx_, int type_)
    {
        if (ctx_ == null || !ctx_.check_tag ()) {
            throw new IllegalStateException();
        }
        SocketBase s = ctx_.create_socket (type_);
        return s;
    }
    
    public static void zmq_close(SocketBase s_) {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        s_.close ();
    }

    public static void zmq_setsockopt(SocketBase s_, int option_, Object optval_) {

        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }

        s_.setsockopt (option_, optval_);

    }

    public static Object zmq_getsockoptx(SocketBase s_, int option_) {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }

        return s_.getsockoptx (option_);
    }

    public static int zmq_getsockopt(SocketBase s_, int opt) {
        
        return s_.getsockopt(opt);
    }

    public static boolean zmq_socket_monitor(SocketBase s_, final String addr_, int events_) {

        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        
        return s_.monitor (addr_, events_);
    }


    public static boolean zmq_bind(SocketBase s_, final String addr_) {
        
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        
        return s_.bind(addr_);
    }
    
    public static boolean zmq_connect (SocketBase s_, String addr_)
    {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        return s_.connect (addr_);
    }
    
    public static boolean zmq_unbind(SocketBase s_, String addr_) {
        
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        return s_.term_endpoint (addr_);
    }
    
    public static boolean zmq_disconnect(SocketBase s_, String addr_) {

        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        return s_.term_endpoint (addr_);
    }

    // Sending functions.
    public static int zmq_send(SocketBase s_, String str, 
            int flags_) {
        byte [] data = str.getBytes(CHARSET);
        return zmq_send(s_, data, data.length, flags_);
    }

    public static int zmq_send(SocketBase s_, Msg msg, 
            int flags_) {
        
        int rc = s_sendmsg (s_, msg, flags_);
        if (rc < 0) {
            return -1;
        }
        
        return rc;
    }
    
    public static int zmq_send(SocketBase s_, byte[] buf_, int len_,
            int flags_) {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        
        Msg msg = new Msg(len_);
        msg.put(buf_, 0, len_);

        int rc = s_sendmsg (s_, msg, flags_);
        if (rc < 0) {
            return -1;
        }
        
        return rc;
    }

    // Send multiple messages.
    //
    // If flag bit ZMQ_SNDMORE is set the vector is treated as
    // a single multi-part message, i.e. the last message has
    // ZMQ_SNDMORE bit switched off.
    //
    public int zmq_sendiov (SocketBase s_, byte[][] a_, int count_, int flags_)
    {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        int rc = 0;
        Msg msg;
       
        for (int i = 0; i < count_; ++i) {
            msg = new Msg(a_[i]);
            if (i == count_ - 1)
                flags_ = flags_ & ~ZMQ_SNDMORE;
            rc = s_sendmsg (s_, msg, flags_);
            if (rc < 0) {
               rc = -1;
               break;
            }
        }
        return rc;

    }

    private static int s_sendmsg(SocketBase s_, Msg msg_, int flags_) {
        int sz = zmq_msg_size (msg_);
        boolean rc = s_.send ( msg_, flags_);
        if ( !rc )
            return -1;
        return sz;
    }


    // Receiving functions.
    
    public static Msg zmq_recv (SocketBase s_, int flags_)
    {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }
        Msg msg = s_recvmsg (s_, flags_);
        if (msg == null) {
            return null;
        }

        //  At the moment an oversized message is silently truncated.
        //  TODO: Build in a notification mechanism to report the overflows.
        //int to_copy = nbytes < len_ ? nbytes : len_;

        return msg;
    }
    
     // Receive a multi-part message
     // 
     // Receives up to *count_ parts of a multi-part message.
     // Sets *count_ to the actual number of parts read.
     // ZMQ_RCVMORE is set to indicate if a complete multi-part message was read.
     // Returns number of message parts read, or -1 on error.
     //
     // Note: even if -1 is returned, some parts of the message
     // may have been read. Therefore the client must consult
     // *count_ to retrieve message parts successfully read,
     // even if -1 is returned.
     //
     // The iov_base* buffers of each iovec *a_ filled in by this 
     // function may be freed using free().
     //
     // Implementation note: We assume zmq::msg_t buffer allocated
     // by zmq::recvmsg can be freed by free().
     // We assume it is safe to steal these buffers by simply
     // not closing the zmq::msg_t.
     //
    public int zmq_recviov (SocketBase s_, byte[][] a_, int count_, int flags_)
    {
        if (s_ == null || !s_.check_tag ()) {
            throw new IllegalStateException();
        }

        int nread = 0;
        boolean recvmore = true;

        for (int i = 0; recvmore && i < count_; ++i) {
            // Cheat! We never close any msg
            // because we want to steal the buffer.
            Msg msg = s_recvmsg (s_, flags_);
            if (msg == null) {
                nread = -1;
                break;
            }

            // Cheat: acquire zmq_msg buffer.
            a_[i] = msg.data();

            // Assume zmq_socket ZMQ_RVCMORE is properly set.
            recvmore = msg.hasMore();
        }
        return nread;
    }


    public static Msg s_recvmsg (SocketBase s_, int flags_)
    {
        return s_.recv (flags_);
    }
    
    public static Msg zmq_msg_init() {
        return new Msg();
    }
    
    public static Msg zmq_msg_init_size(int message_size) {
        return new Msg(message_size);
    }

    public static int zmq_msg_size(Msg msg_) {
        return msg_.size();
    }

    public static Msg zmq_recvmsg(SocketBase s_, int flags_) {
        return zmq_recv (s_, flags_);
    }

    public static int zmq_sendmsg(SocketBase s_, Msg msg_, int flags_) {
        return zmq_send (s_, msg_, flags_);
    }

    public static int zmq_msg_get(Msg msg_) {
        return zmq_msg_get (msg_, ZMQ_MORE);
    }

    public static int zmq_msg_get (Msg msg_, int option_)
    {
        switch (option_) {
            case ZMQ_MORE:
                return msg_.hasMore() ? 1: 0;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static void zmq_sleep(int s) {
        try {
            Thread.sleep(s*(1000L));
        } catch (InterruptedException e) {
        }
        
    }

    //  The proxy functionality
    public static boolean zmq_proxy (SocketBase frontend_, SocketBase backend_, SocketBase control_)
    {
        if (frontend_ == null || backend_ == null) {
            throw new IllegalArgumentException();
        }
        return Proxy.proxy (
            frontend_,
            backend_,
            control_);
    }

    @Deprecated
    public static boolean zmq_device (int device_, SocketBase insocket_,
            SocketBase outsocket_)
    {
        return Proxy.proxy(insocket_, outsocket_, null);
    }

    /**
     * Polling on items. This has very poor performance.
     * Try to use zmq_poll with selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param items_
     * @param timeout_
     * @return number of events
     */
    public static int zmq_poll(PollItem[] items_, long timeout_) {
        return zmq_poll(items_, items_.length, timeout_);
    }

    /**
     * Polling on items. This has very poor performance.
     * Try to use zmq_poll with selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param items_
     * @param timeout_
     * @return number of events
     */
    public static int zmq_poll(PollItem[] items_, int count, long timeout_) {
        Selector selector = null;
        try {
            selector = PollSelector.open();
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }

        int ret = zmq_poll (selector, items_, count, timeout_);

        //  Do not close selector
        
        return ret;
    }

    /**
     * Polling on items with given selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param selector Open and reuse this selector and do not forget to close when it is not used.
     * @param items_
     * @param timeout_
     * @return number of events
     */
    public static int zmq_poll(Selector selector, PollItem[] items_, long timeout_)
    {
        return zmq_poll(selector, items_, items_.length, timeout_);
    }
    /**
     * Polling on items with given selector
     * CAUTION: This could be affected by jdk epoll bug
     *
     * @param selector Open and reuse this selector and do not forget to close when it is not used.
     * @param items_
     * @param count
     * @param timeout_
     * @return number of events
     */
    public static int zmq_poll(Selector selector, PollItem[] items_, int count, long timeout_)
    {
        if (items_ == null) {
            throw new IllegalArgumentException();
        }
        if (count == 0) {
            if (timeout_ == 0)
                return 0;
            try {
                Thread.sleep (timeout_);
            } catch (InterruptedException e) {
            }
            return 0;
        }
        long now = 0;
        long end = 0;
        
        HashMap<SelectableChannel, SelectionKey> saved = new HashMap<SelectableChannel, SelectionKey> ();
        for (SelectionKey key: selector.keys ()) {
            if (key.isValid ())
                saved.put(key.channel (), key);
        }

        for (int i = 0; i < count; i++) {
            PollItem item = items_[i];
            if (item == null) 
                continue;

            SelectableChannel ch = item.getChannel (); // mailbox channel if ZMQ socket
            SelectionKey key = saved.remove (ch);
            
            if (key != null) {
                if (key.interestOps() != item.interestOps()) {
                    key.interestOps(item.interestOps());
                }
                key.attach (item);
            } else {
                try {
                    ch.register(selector, item.interestOps(), item);
                } catch (ClosedChannelException e) {
                    throw new ZError.IOException(e);
                }
            } 
        }

        if (!saved.isEmpty ()) {
            for (SelectionKey deprecated: saved.values ()) {
                deprecated.cancel ();
            }
        }
        
        boolean first_pass = true;
        int nevents = 0;
        int ready;
        
        while (true) {

            //  Compute the timeout for the subsequent poll.
            long timeout;
            if (first_pass)
                timeout = 0;
            else if (timeout_ < 0)
                timeout = -1;
            else
                timeout = end - now;
            
            //  Wait for events.
            try {
                int rc = 0;
                if (timeout < 0)
                    rc = selector.select (0);
                else if (timeout == 0)
                    rc = selector.selectNow ();
                else
                    rc = selector.select (timeout);

                for (SelectionKey key: selector.keys()) {
                    PollItem item = (PollItem) key.attachment ();
                    ready = item.readyOps (key, rc);
                    if (ready < 0)
                        return -1;
                    
                    if (ready > 0) {
                        nevents++;
                    }
                }
                selector.selectedKeys ().clear ();
                
            } catch (IOException e) {
                throw new ZError.IOException (e);
            }            
            //  If timeout is zero, exit immediately whether there are events or not.
            if (timeout_ == 0)
                break;

            if (nevents > 0)
                break;
            
            //  At this point we are meant to wait for events but there are none.
            //  If timeout is infinite we can just loop until we get some events.
            if (timeout_ < 0) {
                if (first_pass)
                    first_pass = false;
                continue;
            }

            //  The timeout is finite and there are no events. In the first pass
            //  we get a timestamp of when the polling have begun. (We assume that
            //  first pass have taken negligible time). We also compute the time
            //  when the polling should time out.
            if (first_pass) {
                now = Clock.now_ms ();
                end = now + timeout_;
                if (now == end)
                    break;
                first_pass = false;
                continue;
            }

            //  Find out whether timeout have expired.
            now = Clock.now_ms ();
            if (now >= end)
                break;
            
            
        }
        return nevents;
    }
    
    public static long zmq_stopwatch_start() {
        return System.nanoTime();
    }

    public static long zmq_stopwatch_stop(long watch) {
        return (System.nanoTime() - watch) / 1000;
    }

    public static int ZMQ_MAKE_VERSION(int major, int minor, int patch) {
        return ((major) * 10000 + (minor) * 100 + (patch));
    }

    public static String zmq_strerror(int errno) {
        return "Errno = " + errno;
    }

    private static final ThreadLocal <PollSelector> POLL_SELECTOR = new ThreadLocal <PollSelector> ();

    // GC closes selector handle
    private static class PollSelector {

        private Selector selector;

        private PollSelector (Selector selector) {
            this.selector = selector;
        }

        public static Selector open () throws IOException
        {
            PollSelector polls = POLL_SELECTOR.get();
            if (polls == null) {
                synchronized (POLL_SELECTOR) {
                    polls = POLL_SELECTOR.get();
                    try {
                        if (polls == null) {
                            polls = new PollSelector (Selector.open ());
                            POLL_SELECTOR.set (polls);
                        }
                    } catch (IOException e) {
                        throw new ZError.IOException(e);
                    }
                }
            }
            return polls.get();
        }

        public Selector get ()
        {
            assert (selector != null);
            assert (selector.isOpen ());
            return selector;
        }

        @Override
        public void finalize ()
        {
            try {
                selector.close ();
            } catch (IOException e) {
            }
            try {
                super.finalize ();
            } catch (Throwable e) {
            }
        }
    }
}
