/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2011 VMware, Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import zmq.TcpAddress.TcpAddressMask;

public class Options {
    //  High-water marks for message pipes.
    int sndhwm;
    int rcvhwm;

    //  I/O thread affinity.
    long affinity;

    //  Socket identity
    byte identity_size;
    byte[] identity; // [256];

    // Last socket endpoint resolved URI
    String last_endpoint;

    //  Maximum tranfer rate [kb/s]. Default 100kb/s.
    int rate;

    //  Reliability time interval [ms]. Default 10 seconds.
    int recovery_ivl;

    // Sets the time-to-live field in every multicast packet sent.
    int multicast_hops;

    // SO_SNDBUF and SO_RCVBUF to be passed to underlying transport sockets.
    int sndbuf;
    int rcvbuf;

    //  Socket type.
    int type;

    //  Linger time, in milliseconds.
    int linger;

    //  Minimum interval between attempts to reconnect, in milliseconds.
    //  Default 100ms
    int reconnect_ivl;
    //  Maximum interval between attempts to reconnect, in milliseconds.
    //  Default 0 (unused)
    int reconnect_ivl_max;

    //  Maximum backlog for pending connections.
    int backlog;

    //  Maximal size of message to handle.
    long maxmsgsize;

    // The timeout for send/recv operations for this socket.
    int rcvtimeo;
    int sndtimeo;

    //  If 1, indicates the use of IPv4 sockets only, it will not be
    //  possible to communicate with IPv6-only hosts. If 0, the socket can
    //  connect to and accept connections from both IPv4 and IPv6 hosts.
    int ipv4only;

    //  If 1, connecting pipes are not attached immediately, meaning a send()
    //  on a socket with only connecting pipes would block
    int delay_attach_on_connect;
    
    //  If true, session reads all the pending messages from the pipe and
    //  sends them to the network when socket is closed.
    boolean delay_on_close;

    //  If true, socket reads all the messages from the pipe and delivers
    //  them to the user when the peer terminates.
    boolean delay_on_disconnect;

    //  If 1, (X)SUB socket should filter the messages. If 0, it should not.
    boolean filter;

    //  If true, the identity message is forwarded to the socket.
    boolean recv_identity;

    //  TCP keep-alive settings.
    //  Defaults to -1 = do not change socket options
    int tcp_keepalive;
    int tcp_keepalive_cnt;
    int tcp_keepalive_idle;
    int tcp_keepalive_intvl;

    // TCP accept() filters
    //typedef std::vector <tcp_address_mask_t> tcp_accept_filters_t;
    final List<TcpAddress.TcpAddressMask> tcp_accept_filters;
    
    //  ID of the socket.
    int socket_id;
    Class<? extends DecoderBase> decoder;
    Class<? extends EncoderBase> encoder;

    public Options() {
        sndhwm = 1000;
        rcvhwm = 1000;
        affinity = 0;
        identity_size = 0;
        rate = 100;
        recovery_ivl = 10000;
        multicast_hops = 1;
        sndbuf = 0;
        rcvbuf = 0;
        type = -1;
        linger = -1;
        reconnect_ivl = 100;
        reconnect_ivl_max = 0;
        backlog = 100;
        maxmsgsize = -1;
        rcvtimeo = -1;
        sndtimeo = -1;
        ipv4only = 1;
        delay_attach_on_connect =  0;
        delay_on_close = true;
        delay_on_disconnect = true;
        filter = false;
        recv_identity = false;
        tcp_keepalive = -1;
        tcp_keepalive_cnt = -1;
        tcp_keepalive_idle = -1;
        tcp_keepalive_intvl = -1;
        socket_id = 0;
        
    	identity = null;
    	tcp_accept_filters = new ArrayList<TcpAddress.TcpAddressMask>();
    	decoder = null;
    	encoder = null;
    }
    
    @SuppressWarnings("unchecked")
    public void setsockopt(int option_, Object optval_) {
        switch (option_) {
        
        case ZMQ.ZMQ_SNDHWM:
            sndhwm = (Integer)optval_;
            if (sndhwm < 0) {
                throw new IllegalArgumentException("sndhwm " + optval_);
            }
            return;
            
        case ZMQ.ZMQ_RCVHWM:
            rcvhwm = (Integer)optval_;
            if (rcvhwm < 0) {
                throw new IllegalArgumentException("rcvhwm " + optval_);
            }
            return ;

        case ZMQ.ZMQ_AFFINITY:
            affinity = (Long)optval_;
            return;
            
        case ZMQ.ZMQ_IDENTITY:
            byte[] val;
            
            if (optval_ instanceof String)
                val = ((String) optval_).getBytes(ZMQ.CHARSET);
            else if (optval_ instanceof byte[])
                val = (byte[]) optval_;
            else {
                throw new IllegalArgumentException("identity " + optval_);
            }
            
            if (val == null || val.length > 255) {
                throw new IllegalArgumentException("identity must not be null or less than 255 " + optval_);
            }
            identity = Arrays.copyOf(val, val.length);
            identity_size = (byte)identity.length;
            return;
            
        case ZMQ.ZMQ_RATE:
            rate = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_RECOVERY_IVL:
            recovery_ivl = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_SNDBUF:
            sndbuf = (Integer)optval_;
            return;
           
        case ZMQ.ZMQ_RCVBUF:
            rcvbuf = (Integer)optval_;
            return;
           
        case ZMQ.ZMQ_LINGER:
            linger = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_RECONNECT_IVL:
            reconnect_ivl = (Integer)optval_;
            
            if (reconnect_ivl < -1) {
                throw new IllegalArgumentException("reconnect_ivl " + optval_);
            }
            
            return;
            
        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            reconnect_ivl_max = (Integer)optval_;
            
            if (reconnect_ivl_max < 0) {
                throw new IllegalArgumentException("reconnect_ivl_max " + optval_);
            }
            
            return;

        case ZMQ.ZMQ_BACKLOG:
            backlog = (Integer)optval_;
            return;

        case ZMQ.ZMQ_MAXMSGSIZE:
            maxmsgsize = (Long)optval_;
            return;
            
        case ZMQ.ZMQ_MULTICAST_HOPS:
            multicast_hops = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_RCVTIMEO:
            rcvtimeo = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_SNDTIMEO:
            sndtimeo = (Integer)optval_;
            return;
            
        case ZMQ.ZMQ_IPV4ONLY:
            
            ipv4only = (Integer)optval_;
            if (ipv4only != 0 && ipv4only != 1) {
                throw new IllegalArgumentException("ipv4only only accepts 0 or 1 " + optval_);
            }
            return;
            
        case ZMQ.ZMQ_TCP_KEEPALIVE:
            
            tcp_keepalive = (Integer)optval_;
            if (tcp_keepalive != -1 && tcp_keepalive != 0 && tcp_keepalive != 1) {
                throw new IllegalArgumentException("tcp_keepalive only accepts one of -1,0,1 " + optval_);
            }
            return;
            
        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:
            
            delay_attach_on_connect = (Integer) optval_;
            if (delay_attach_on_connect !=0 && delay_attach_on_connect != 1) {
                throw new IllegalArgumentException("delay_attach_on_connect only accept 0 or 1 " + optval_);
            }
            return;
            
        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return;
            
        case ZMQ.ZMQ_TCP_ACCEPT_FILTER:
            String filter_str = (String) optval_;
            if (filter_str == null) {
                tcp_accept_filters.clear();
            } else if (filter_str.length() == 0 || filter_str.length() > 255) {
                throw new IllegalArgumentException("tcp_accept_filter " + optval_);
            } else {
                TcpAddressMask filter = new TcpAddressMask();
                filter.resolve (filter_str, ipv4only==1 ? true : false);
                tcp_accept_filters.add(filter);
            }
            return;
            
        case ZMQ.ZMQ_ENCODER:
            if (optval_ instanceof String) {
                try {
                    encoder = Class.forName((String) optval_).asSubclass(EncoderBase.class);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            } else if (optval_ instanceof Class) {
                encoder = (Class<? extends EncoderBase>) optval_;
            } else {
                throw new IllegalArgumentException("encoder " + optval_);
            }
            return;
            
        case ZMQ.ZMQ_DECODER:
            if (optval_ instanceof String) {
                try {
                    decoder = Class.forName((String) optval_).asSubclass(DecoderBase.class);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            } else if (optval_ instanceof Class) {
                decoder = (Class<? extends DecoderBase>) optval_;
            } else {
                throw new IllegalArgumentException("decoder " + optval_);
            }
            return;

        default:
            throw new IllegalArgumentException("Unknown Option " + option_);
        }
    }

    
    public Object getsockopt(int option_) {
        
        switch (option_) {
        
        case ZMQ.ZMQ_SNDHWM:
            return sndhwm;
            
        case ZMQ.ZMQ_RCVHWM:
            return rcvhwm;            

        case ZMQ.ZMQ_AFFINITY:
            return affinity;
            
        case ZMQ.ZMQ_IDENTITY:
            return identity;
            
        case ZMQ.ZMQ_RATE:
            return rate; 
            
        case ZMQ.ZMQ_RECOVERY_IVL:
            return recovery_ivl;
            
        case ZMQ.ZMQ_SNDBUF:
           return sndbuf;
           
        case ZMQ.ZMQ_RCVBUF:
           return rcvbuf;
           
        case ZMQ.ZMQ_TYPE:
            return type;
           
        case ZMQ.ZMQ_LINGER:
            return linger;
            
        case ZMQ.ZMQ_RECONNECT_IVL:
            return reconnect_ivl;
            
        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            return reconnect_ivl_max; 

        case ZMQ.ZMQ_BACKLOG:
            return backlog; 
          
        case ZMQ.ZMQ_MAXMSGSIZE:
            return maxmsgsize;
            
        case ZMQ.ZMQ_MULTICAST_HOPS:
            return multicast_hops;
            
        case ZMQ.ZMQ_RCVTIMEO:
            return rcvtimeo;
            
        case ZMQ.ZMQ_SNDTIMEO:
            return sndtimeo;
            
        case ZMQ.ZMQ_IPV4ONLY:
            return ipv4only;
            
        case ZMQ.ZMQ_TCP_KEEPALIVE:
            return tcp_keepalive; 
            
        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:
            return delay_attach_on_connect;
                    
        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return 0;
            
        case ZMQ.ZMQ_LAST_ENDPOINT:
            return last_endpoint;
            
        default:
            throw new IllegalArgumentException("option=" + option_);
        }
    }
    
}
