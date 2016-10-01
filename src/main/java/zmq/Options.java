package zmq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import zmq.TcpAddress.TcpAddressMask;

public class Options
{
    //  High-water marks for message pipes.
    int sendHwm;
    int recvHwm;

    //  I/O thread affinity.
    long affinity;

    //  Socket identity
    byte identitySize;
    byte[] identity; // [256];

    // Last socket endpoint resolved URI
    String lastEndpoint;

    //  Maximum tranfer rate [kb/s]. Default 100kb/s.
    int rate;

    //  Reliability time interval [ms]. Default 10 seconds.
    int recoveryIvl;

    // Sets the time-to-live field in every multicast packet sent.
    int multicastHops;

    // SO_SNDBUF and SO_RCVBUF to be passed to underlying transport sockets.
    int sndbuf;
    int rcvbuf;

    //  Socket type.
    int type;

    //  Linger time, in milliseconds.
    int linger;

    //  Minimum interval between attempts to reconnect, in milliseconds.
    //  Default 100ms
    int reconnectIvl;
    //  Maximum interval between attempts to reconnect, in milliseconds.
    //  Default 0 (unused)
    int reconnectIvlMax;

    // if the REQ socket has correlation enabled (= is sending request IDs)
    int reqCorrelate;

    // if the REQ FSM is not strictly adhered to
    int reqRelaxed;

    //  Maximum backlog for pending connections.
    int backlog;

    //  Maximal size of message to handle.
    long maxMsgSize;

    // The timeout for send/recv operations for this socket.
    int recvTimeout;
    int sendTimeout;

    //  If 1, indicates the use of IPv4 sockets only, it will not be
    //  possible to communicate with IPv6-only hosts. If 0, the socket can
    //  connect to and accept connections from both IPv4 and IPv6 hosts.
    int ipv4only;

    //  If 1, connecting pipes are not attached immediately, meaning a send()
    //  on a socket with only connecting pipes would block
    int delayAttachOnConnect;

    //  If true, session reads all the pending messages from the pipe and
    //  sends them to the network when socket is closed.
    boolean delayOnClose;

    //  If true, socket reads all the messages from the pipe and delivers
    //  them to the user when the peer terminates.
    boolean delayOnDisconnect;

    //  If 1, (X)SUB socket should filter the messages. If 0, it should not.
    boolean filter;

    //  If true, the identity message is forwarded to the socket.
    boolean recvIdentity;

    //  TCP keep-alive settings.
    //  Defaults to -1 = do not change socket options
    int tcpKeepAlive;
    int tcpKeepAliveCnt;
    int tcpKeepAliveIdle;
    int tcpKeepAliveIntvl;

    // TCP accept() filters
    //typedef std::vector <tcp_address_mask_t> tcp_accept_filters_t;
    final List<TcpAddress.TcpAddressMask> tcpAcceptFilters;

    //  ID of the socket.
    int socketId;
    Class<? extends DecoderBase> decoder;
    Class<? extends EncoderBase> encoder;
    MsgAllocator msgAllocator;

    public Options()
    {
        sendHwm = 1000;
        recvHwm = 1000;
        affinity = 0;
        identitySize = 0;
        rate = 100;
        recoveryIvl = 10000;
        multicastHops = 1;
        sndbuf = 0;
        rcvbuf = 0;
        type = -1;
        linger = -1;
        reconnectIvl = 100;
        reconnectIvlMax = 0;
        reqCorrelate = 0;
        reqRelaxed = 0;
        backlog = 100;
        maxMsgSize = -1;
        recvTimeout = -1;
        sendTimeout = -1;
        ipv4only = 1;
        delayAttachOnConnect =  0;
        delayOnClose = true;
        delayOnDisconnect = true;
        filter = false;
        recvIdentity = false;
        tcpKeepAlive = -1;
        tcpKeepAliveCnt = -1;
        tcpKeepAliveIdle = -1;
        tcpKeepAliveIntvl = -1;
        socketId = 0;

        identity = null;
        tcpAcceptFilters = new ArrayList<TcpAddress.TcpAddressMask>();
        decoder = null;
        encoder = null;
        msgAllocator = null;
    }

    @SuppressWarnings("unchecked")
    public void setSocketOpt(int option, Object optval)
    {
        switch (option) {
        case ZMQ.ZMQ_SNDHWM:
            sendHwm = (Integer) optval;
            if (sendHwm < 0) {
                throw new IllegalArgumentException("sendHwm " + optval);
            }
            return;

        case ZMQ.ZMQ_RCVHWM:
            recvHwm = (Integer) optval;
            if (recvHwm < 0) {
                throw new IllegalArgumentException("recvHwm " + optval);
            }
            return;

        case ZMQ.ZMQ_AFFINITY:
            affinity = (Long) optval;
            return;

        case ZMQ.ZMQ_IDENTITY:
            byte[] val;

            if (optval instanceof String) {
                val = ((String) optval).getBytes(ZMQ.CHARSET);
            }
            else if (optval instanceof byte[]) {
                val = (byte[]) optval;
            }
            else {
                throw new IllegalArgumentException("identity " + optval);
            }

            if (val == null || val.length > 255) {
                throw new IllegalArgumentException("identity must not be null or less than 255 " + optval);
            }
            identity = Arrays.copyOf(val, val.length);
            identitySize = (byte) identity.length;
            return;

        case ZMQ.ZMQ_RATE:
            rate = (Integer) optval;
            return;

        case ZMQ.ZMQ_RECOVERY_IVL:
            recoveryIvl = (Integer) optval;
            return;

        case ZMQ.ZMQ_SNDBUF:
            sndbuf = (Integer) optval;
            return;

        case ZMQ.ZMQ_RCVBUF:
            rcvbuf = (Integer) optval;
            return;

        case ZMQ.ZMQ_LINGER:
            linger = (Integer) optval;
            return;

        case ZMQ.ZMQ_RECONNECT_IVL:
            reconnectIvl = (Integer) optval;

            if (reconnectIvl < -1) {
                throw new IllegalArgumentException("reconnectIvl " + optval);
            }

            return;

        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            reconnectIvlMax = (Integer) optval;

            if (reconnectIvlMax < 0) {
                throw new IllegalArgumentException("reconnectIvlMax " + optval);
            }

                return;

        case ZMQ.ZMQ_REQ_CORRELATE:
            reqCorrelate = (Integer) optval;
            return;

        case ZMQ.ZMQ_REQ_RELAXED:
            reqRelaxed = (Integer) optval;
            return;

        case ZMQ.ZMQ_BACKLOG:
            backlog = (Integer) optval;
            return;

        case ZMQ.ZMQ_MAXMSGSIZE:
            maxMsgSize = (Long) optval;
            return;

        case ZMQ.ZMQ_MULTICAST_HOPS:
            multicastHops = (Integer) optval;
            return;

        case ZMQ.ZMQ_RCVTIMEO:
            recvTimeout = (Integer) optval;
            return;

        case ZMQ.ZMQ_SNDTIMEO:
            sendTimeout = (Integer) optval;
            return;

        case ZMQ.ZMQ_IPV4ONLY:

            ipv4only = (Integer) optval;
            if (ipv4only != 0 && ipv4only != 1) {
                throw new IllegalArgumentException("ipv4only only accepts 0 or 1 " + optval);
            }
            return;

        case ZMQ.ZMQ_TCP_KEEPALIVE:

            tcpKeepAlive = (Integer) optval;
            if (tcpKeepAlive != -1 && tcpKeepAlive != 0 && tcpKeepAlive != 1) {
                throw new IllegalArgumentException("tcpKeepAlive only accepts one of -1,0,1 " + optval);
            }
            return;

        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:

            delayAttachOnConnect = (Integer) optval;
            if (delayAttachOnConnect != 0 && delayAttachOnConnect != 1) {
                throw new IllegalArgumentException("delayAttachOnConnect only accept 0 or 1 " + optval);
            }
            return;

        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return;

        case ZMQ.ZMQ_TCP_ACCEPT_FILTER:
            String filterStr = (String) optval;
            if (filterStr == null) {
                tcpAcceptFilters.clear();
            }
            else if (filterStr.length() == 0 || filterStr.length() > 255) {
                throw new IllegalArgumentException("tcp_accept_filter " + optval);
            }
            else {
                TcpAddressMask filter = new TcpAddressMask();
                filter.resolve(filterStr, ipv4only == 1);
                tcpAcceptFilters.add(filter);
            }
            return;

        case ZMQ.ZMQ_ENCODER:
            if (optval instanceof String) {
                try {
                    encoder = Class.forName((String) optval).asSubclass(EncoderBase.class);
                }
                catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            else if (optval instanceof Class) {
                encoder = (Class<? extends EncoderBase>) optval;
            }
            else {
                throw new IllegalArgumentException("encoder " + optval);
            }
            return;

        case ZMQ.ZMQ_DECODER:
            if (optval instanceof String) {
                try {
                    decoder = Class.forName((String) optval).asSubclass(DecoderBase.class);
                }
                catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            else if (optval instanceof Class) {
                decoder = (Class<? extends DecoderBase>) optval;
            }
            else {
                throw new IllegalArgumentException("decoder " + optval);
            }
            return;
        case ZMQ.ZMQ_MSG_ALLOCATOR:
           if (optval instanceof String) {
               try {
                   Class<? extends MsgAllocator> msgAllocatorClass = Class.forName((String) optval).asSubclass(MsgAllocator.class);
                   msgAllocator = msgAllocatorClass.newInstance();
               }
               catch (ClassNotFoundException e) {
                   throw new IllegalArgumentException(e);
               }
               catch (InstantiationException e) {
                  throw new IllegalArgumentException(e);
               }
               catch (IllegalAccessException e) {
                  throw new IllegalArgumentException(e);
               }
           }
           else if (optval instanceof Class) {
              try {
                 Class<? extends MsgAllocator> msgAllocatorClass = (Class<? extends MsgAllocator>) optval;
                 msgAllocator = msgAllocatorClass.newInstance();
              }
              catch (InstantiationException e) {
                 throw new IllegalArgumentException(e);
              }
              catch (IllegalAccessException e) {
                 throw new IllegalArgumentException(e);
              }
           }
           else if (optval instanceof MsgAllocator) {
              msgAllocator = (MsgAllocator) optval;
           }
           else {
               throw new IllegalArgumentException("msgAllocator " + optval);
           }
           return;

        default:
            throw new IllegalArgumentException("Unknown Option " + option);
        }
    }

    public Object getsockopt(int option)
    {
        switch (option) {
        case ZMQ.ZMQ_SNDHWM:
            return sendHwm;

        case ZMQ.ZMQ_RCVHWM:
            return recvHwm;

        case ZMQ.ZMQ_AFFINITY:
            return affinity;

        case ZMQ.ZMQ_IDENTITY:
            return identity;

        case ZMQ.ZMQ_RATE:
            return rate;

        case ZMQ.ZMQ_RECOVERY_IVL:
            return recoveryIvl;

        case ZMQ.ZMQ_SNDBUF:
           return sndbuf;

        case ZMQ.ZMQ_RCVBUF:
           return rcvbuf;

        case ZMQ.ZMQ_TYPE:
            return type;

        case ZMQ.ZMQ_LINGER:
            return linger;

        case ZMQ.ZMQ_RECONNECT_IVL:
            return reconnectIvl;

        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            return reconnectIvlMax;

            case ZMQ.ZMQ_REQ_CORRELATE:
                return reqCorrelate;

            case ZMQ.ZMQ_REQ_RELAXED:
                return reqRelaxed;

        case ZMQ.ZMQ_BACKLOG:
            return backlog;

        case ZMQ.ZMQ_MAXMSGSIZE:
            return maxMsgSize;

        case ZMQ.ZMQ_MULTICAST_HOPS:
            return multicastHops;

        case ZMQ.ZMQ_RCVTIMEO:
            return recvTimeout;

        case ZMQ.ZMQ_SNDTIMEO:
            return sendTimeout;

        case ZMQ.ZMQ_IPV4ONLY:
            return ipv4only;

        case ZMQ.ZMQ_TCP_KEEPALIVE:
            return tcpKeepAlive;

        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:
            return delayAttachOnConnect;

        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return 0;

        case ZMQ.ZMQ_LAST_ENDPOINT:
            return lastEndpoint;

        default:
            throw new IllegalArgumentException("option=" + option);
        }
    }
}
