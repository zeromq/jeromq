package zmq;

import java.util.ArrayList;
import java.util.List;

public class Options {
    //  High-water marks for message pipes.
    int sndhwm;
    int rcvhwm;

    //  I/O thread affinity.
    long affinity;

    //  Socket identity
    byte identity_size;
    final byte[] identity; // [256];

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

    //  If true, session reads all the pending messages from the pipe and
    //  sends them to the network when socket is closed.
    boolean delay_on_close;

    //  If true, socket reads all the messages from the pipe and delivers
    //  them to the user when the peer terminates.
    boolean delay_on_disconnect;

    //  If 1, (X)SUB socket should filter the messages. If 0, it should not.
    boolean filter;

    //  Sends identity to all new connections.
    boolean send_identity;

    //  Receivers identity from all new connections.
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
        delay_on_close = true;
        delay_on_disconnect = true;
        filter = false;
        send_identity = false;
        recv_identity = false;
        tcp_keepalive = -1;
        tcp_keepalive_cnt = -1;
        tcp_keepalive_idle = -1;
        tcp_keepalive_intvl = -1;
        socket_id = 0;
        
    	identity = new byte[256];
    	tcp_accept_filters = new ArrayList<TcpAddress.TcpAddressMask>();
    }

    public void setsockopt(int option_, int optval_) {
        // TODO Auto-generated method stub
        switch (option_) {

        case ZMQ.ZMQ_SNDHWM:
            sndhwm = optval_;
            return;
        case ZMQ.ZMQ_RCVHWM:
            rcvhwm = optval_;
            return;
        default:
            throw new IllegalArgumentException("option=" + option_);
        }
    }
}
