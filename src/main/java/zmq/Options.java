package zmq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import zmq.io.coder.IDecoder;
import zmq.io.coder.IEncoder;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.SelectorProviderChooser;
import zmq.io.net.ipc.IpcAddress;
import zmq.io.net.tcp.TcpAddress;
import zmq.io.net.tcp.TcpAddress.TcpAddressMask;
import zmq.msg.MsgAllocator;
import zmq.msg.MsgAllocatorThreshold;
import zmq.util.Errno;
import zmq.util.ValueReference;
import zmq.util.Z85;

public class Options
{
    //  High-water marks for message pipes.
    public int sendHwm;
    public int recvHwm;

    //  I/O thread affinity.
    public long affinity;

    //  Socket identity
    public byte   identitySize;
    public byte[] identity;    // [256];

    // Last socket endpoint resolved URI
    String lastEndpoint;

    //  Maximum tranfer rate [kb/s]. Default 100kb/s.
    int rate;

    //  Reliability time interval [ms]. Default 10 seconds.
    int recoveryIvl;

    // Sets the time-to-live field in every multicast packet sent.
    int multicastHops;

    // SO_SNDBUF and SO_RCVBUF to be passed to underlying transport sockets.
    public int sndbuf;
    public int rcvbuf;

    // Type of service (containing DSCP and ECN socket options)
    public int tos;

    //  Socket type.
    public int type;

    //  Linger time, in milliseconds.
    public int linger;

    //  Minimum interval between attempts to reconnect, in milliseconds.
    //  Default 100ms
    public int reconnectIvl;
    //  Maximum interval between attempts to reconnect, in milliseconds.
    //  Default 0 (unused)
    public int reconnectIvlMax;

    //  Maximum backlog for pending connections.
    public int backlog;

    //  Maximal size of message to handle.
    public long maxMsgSize;

    // The timeout for send/recv operations for this socket.
    int recvTimeout;
    int sendTimeout;

    //  If true, IPv6 is enabled (as well as IPv4)
    public boolean ipv6;

    //  If false, connecting pipes are not attached immediately, meaning a send()
    //  on a socket with only connecting pipes would block
    public boolean immediate;

    //  If 1, (X)SUB socket should filter the messages. If 0, it should not.
    public boolean filter;

    //  If true, the identity message is forwarded to the socket.
    public boolean recvIdentity;

    // if true, router socket accepts non-zmq tcp connections
    public boolean rawSocket;

    //  Addres of SOCKS proxy
    public String socksProxyAddress;

    //  TCP keep-alive settings.
    //  Defaults to -1 = do not change socket options
    public int tcpKeepAlive;
    public int tcpKeepAliveCnt;
    public int tcpKeepAliveIdle;
    public int tcpKeepAliveIntvl;

    // TCP accept() filters
    //typedef std::vector <tcp_address_mask_t> tcp_accept_filters_t;
    public final List<TcpAddress.TcpAddressMask> tcpAcceptFilters = new ArrayList<>();

    // IPC accept() filters
    final List<IpcAddress.IpcAddressMask> ipcAcceptFilters = new ArrayList<>();

    //  Security mechanism for all connections on this socket
    public Mechanisms mechanism = Mechanisms.NULL;

    //  If peer is acting as server for PLAIN or CURVE mechanisms
    public boolean asServer;

    //  ZAP authentication domain
    public String zapDomain = "";

    //  Security credentials for PLAIN mechanism
    public String plainUsername;
    public String plainPassword;

    //  Security credentials for CURVE mechanism
    //  Normal base 256 key is 32 bytes
    public static final int CURVE_KEYSIZE = 32;
    //  Key encoded using Z85 is 40 bytes
    public static final int CURVE_KEYSIZE_Z85 = 40;
    public byte[]           curvePublicKey;
    public byte[]           curveSecretKey;
    public byte[]           curveServerKey;

    //  Principals for GSSAPI mechanism
    String gssPrincipal;
    String gssServicePrincipal;
    //  If true, gss encryption will be disabled
    boolean gssPlaintext;

    //  ID of the socket.
    public int socketId;

    //  If true, socket conflates outgoing/incoming messages.
    //  Applicable to dealer, push/pull, pub/sub socket types.
    //  Cannot receive multi-part messages.
    //  Ignores hwm
    public boolean conflate;

    //  If connection handshake is not done after this many milliseconds,
    //  close socket.  Default is 30 secs.  0 means no handshake timeout.
    public int handshakeIvl;

    //  If remote peer receives a PING message and doesn't receive another
    //  message within the ttl value, it should close the connection
    //  (measured in tenths of a second)
    public int heartbeatTtl;
    //  Time in milliseconds between sending heartbeat PING messages.
    public int heartbeatInterval;
    //  Time in milliseconds to wait for a PING response before disconnecting
    public int heartbeatTimeout;
    // the ping context that will be sent with each ping message.
    public byte[] heartbeatContext;

    public Class<? extends IDecoder> decoder;
    public Class<? extends IEncoder> encoder;

    // threshold to allocate byte buffers on direct memory instead of heap.
    // Set to <= 0 to disable this system.
    public MsgAllocator allocator;

    public SelectorProviderChooser selectorChooser;

    public final Errno errno = new Errno();

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
        tos = 0;
        type = -1;
        linger = -1;
        reconnectIvl = 100;
        reconnectIvlMax = 0;
        backlog = 100;
        maxMsgSize = -1;
        recvTimeout = -1;
        sendTimeout = -1;
        ipv6 = "false".equals(System.getProperty("java.net.preferIPv4Stack", null)) ||
               "true".equals(System.getProperty("java.net.preferIPv6Addresses", null));
        immediate = true;
        filter = false;
        recvIdentity = false;
        rawSocket = false;
        tcpKeepAlive = -1;
        tcpKeepAliveCnt = -1;
        tcpKeepAliveIdle = -1;
        tcpKeepAliveIntvl = -1;
        mechanism = Mechanisms.NULL;
        asServer = false;
        gssPlaintext = false;
        socketId = 0;
        conflate = false;
        handshakeIvl = 30000;

        heartbeatTtl = 0;
        heartbeatInterval = 0;
        heartbeatTimeout = -1;
        heartbeatContext = new byte[0];

        identity = new byte[0];
        identitySize = (byte) identity.length;

        curvePublicKey = new byte[CURVE_KEYSIZE];
        curveSecretKey = new byte[CURVE_KEYSIZE];
        curveServerKey = new byte[CURVE_KEYSIZE];

        allocator = new MsgAllocatorThreshold(Config.MSG_ALLOCATION_HEAP_THRESHOLD.getValue());

        selectorChooser = null;
    }

    @SuppressWarnings("deprecation")
    public boolean setSocketOpt(int option, Object optval)
    {
        final ValueReference<Boolean> result = new ValueReference<>(false);
        switch (option) {
        case ZMQ.ZMQ_SNDHWM:
            sendHwm = (Integer) optval;
            if (sendHwm < 0) {
                throw new IllegalArgumentException("sendHwm " + optval);
            }
            return true;

        case ZMQ.ZMQ_RCVHWM:
            recvHwm = (Integer) optval;
            if (recvHwm < 0) {
                throw new IllegalArgumentException("recvHwm " + optval);
            }
            return true;

        case ZMQ.ZMQ_AFFINITY:
            affinity = (Long) optval;
            return true;

        case ZMQ.ZMQ_IDENTITY:
            byte[] val = parseBytes(option, optval);

            if (val == null || val.length > 255) {
                throw new IllegalArgumentException("identity must not be null or less than 255 " + optval);
            }
            identity = Arrays.copyOf(val, val.length);
            identitySize = (byte) identity.length;
            return true;

        case ZMQ.ZMQ_RATE:
            rate = (Integer) optval;
            return true;

        case ZMQ.ZMQ_RECOVERY_IVL:
            recoveryIvl = (Integer) optval;
            return true;

        case ZMQ.ZMQ_SNDBUF:
            sndbuf = (Integer) optval;
            return true;

        case ZMQ.ZMQ_RCVBUF:
            rcvbuf = (Integer) optval;
            return true;

        case ZMQ.ZMQ_TOS:
            tos = (Integer) optval;
            return true;

        case ZMQ.ZMQ_LINGER:
            linger = (Integer) optval;
            return true;

        case ZMQ.ZMQ_RECONNECT_IVL:
            reconnectIvl = (Integer) optval;

            if (reconnectIvl < -1) {
                throw new IllegalArgumentException("reconnectIvl " + optval);
            }

            return true;

        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            reconnectIvlMax = (Integer) optval;

            if (reconnectIvlMax < 0) {
                throw new IllegalArgumentException("reconnectIvlMax " + optval);
            }

            return true;

        case ZMQ.ZMQ_BACKLOG:
            backlog = (Integer) optval;
            return true;

        case ZMQ.ZMQ_MAXMSGSIZE:
            maxMsgSize = (Long) optval;
            return true;

        case ZMQ.ZMQ_MULTICAST_HOPS:
            multicastHops = (Integer) optval;
            return true;

        case ZMQ.ZMQ_RCVTIMEO:
            recvTimeout = (Integer) optval;
            return true;

        case ZMQ.ZMQ_SNDTIMEO:
            sendTimeout = (Integer) optval;
            return true;

        /*  Deprecated in favor of ZMQ_IPV6  */
        case ZMQ.ZMQ_IPV4ONLY:
            return setSocketOpt(ZMQ.ZMQ_IPV6, !parseBoolean(option, optval));

        /*  To replace the somewhat surprising IPV4ONLY */
        case ZMQ.ZMQ_IPV6:
            ipv6 = parseBoolean(option, optval);
            return true;

        case ZMQ.ZMQ_SOCKS_PROXY:
            socksProxyAddress = parseString(option, optval);
            return true;

        case ZMQ.ZMQ_TCP_KEEPALIVE:
            tcpKeepAlive = ((Number) optval).intValue();
            if (tcpKeepAlive != -1 && tcpKeepAlive != 0 && tcpKeepAlive != 1) {
                throw new IllegalArgumentException("tcpKeepAlive only accepts one of -1,0,1 " + optval);
            }
            return true;

        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return false;

        case ZMQ.ZMQ_IMMEDIATE:
            immediate = parseBoolean(option, optval);
            return true;

        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:
            immediate = !parseBoolean(option, optval);
            return true;

        case ZMQ.ZMQ_TCP_ACCEPT_FILTER:
            String filterStr = parseString(option, optval);
            if (filterStr == null) {
                tcpAcceptFilters.clear();
            }
            else if (filterStr.length() == 0 || filterStr.length() > 255) {
                throw new IllegalArgumentException("tcp_accept_filter " + optval);
            }
            else {
                TcpAddressMask filter = new TcpAddressMask(filterStr, ipv6);
                tcpAcceptFilters.add(filter);
            }
            return true;

        case ZMQ.ZMQ_PLAIN_SERVER:
            asServer = parseBoolean(option, optval);
            mechanism = (asServer ? Mechanisms.PLAIN : Mechanisms.NULL);
            return true;

        case ZMQ.ZMQ_PLAIN_USERNAME:
            if (optval == null) {
                mechanism = Mechanisms.NULL;
                asServer = false;
                return true;
            }
            plainUsername = parseString(option, optval);
            asServer = false;
            mechanism = Mechanisms.PLAIN;
            return true;

        case ZMQ.ZMQ_PLAIN_PASSWORD:
            if (optval == null) {
                mechanism = Mechanisms.NULL;
                asServer = false;
                return true;
            }
            plainPassword = parseString(option, optval);
            asServer = false;
            mechanism = Mechanisms.PLAIN;
            return true;

        case ZMQ.ZMQ_ZAP_DOMAIN:
            String domain = parseString(option, optval);
            if (domain != null && domain.length() < 256) {
                zapDomain = domain;
                return true;
            }
            throw new IllegalArgumentException("zap domain length shall be < 256 : " + optval);

        case ZMQ.ZMQ_CURVE_SERVER:
            asServer = parseBoolean(option, optval);
            mechanism = (asServer ? Mechanisms.CURVE : Mechanisms.NULL);
            return true;

        case ZMQ.ZMQ_CURVE_PUBLICKEY:
            curvePublicKey = setCurveKey(option, optval, result);
            return result.get();

        case ZMQ.ZMQ_CURVE_SECRETKEY:
            curveSecretKey = setCurveKey(option, optval, result);
            return result.get();

        case ZMQ.ZMQ_CURVE_SERVERKEY:
            curveServerKey = setCurveKey(option, optval, result);
            if (curveServerKey == null) {
                asServer = false;
            }
            return result.get();

        case ZMQ.ZMQ_CONFLATE:
            conflate = parseBoolean(option, optval);
            return true;

        case ZMQ.ZMQ_GSSAPI_SERVER:
            asServer = parseBoolean(option, optval);
            mechanism = Mechanisms.GSSAPI;
            return true;

        case ZMQ.ZMQ_GSSAPI_PRINCIPAL:
            gssPrincipal = parseString(option, optval);
            mechanism = Mechanisms.GSSAPI;
            return true;

        case ZMQ.ZMQ_GSSAPI_SERVICE_PRINCIPAL:
            gssServicePrincipal = parseString(option, optval);
            mechanism = Mechanisms.GSSAPI;
            return true;

        case ZMQ.ZMQ_GSSAPI_PLAINTEXT:
            gssPlaintext = parseBoolean(option, optval);
            return true;

        case ZMQ.ZMQ_HANDSHAKE_IVL:
            handshakeIvl = (Integer) optval;
            if (handshakeIvl < 0) {
                throw new IllegalArgumentException("handshakeIvl only accept positive values " + optval);
            }
            return true;

        case ZMQ.ZMQ_HEARTBEAT_IVL:
            heartbeatInterval = (Integer) optval;
            if (heartbeatInterval < 0) {
                throw new IllegalArgumentException("heartbeatInterval only accept positive values " + optval);
            }
            return true;

        case ZMQ.ZMQ_HEARTBEAT_TIMEOUT:
            heartbeatTimeout = (Integer) optval;
            if (heartbeatTimeout < 0) {
                throw new IllegalArgumentException("heartbeatTimeout only accept positive values " + optval);
            }
            return true;

        case ZMQ.ZMQ_HEARTBEAT_TTL:
            Integer value = (Integer) optval;
            // Convert this to deciseconds from milliseconds
            value /= 100;

            if (value >= 0 && value <= 6553) {
                heartbeatTtl = value;
            }
            else {
                throw new IllegalArgumentException("heartbeatTtl is out of range [0..655399]" + optval);
            }
            return true;

        case ZMQ.ZMQ_HEARTBEAT_CONTEXT:
            heartbeatContext = (byte[]) optval;
            if (heartbeatContext == null) {
                throw new IllegalArgumentException("heartbeatContext cannot be null");
            }
            return true;

        case ZMQ.ZMQ_DECODER:
            decoder = checkCustomCodec(optval, IDecoder.class);
            rawSocket = true;
            // failure throws ZError.InstantiationException
            // if that line is reached, everything is fine
            return true;

        case ZMQ.ZMQ_ENCODER:
            encoder = checkCustomCodec(optval, IEncoder.class);
            rawSocket = true;
            // failure throws ZError.InstantiationException
            // if that line is reached, everything is fine
            return true;

        case ZMQ.ZMQ_MSG_ALLOCATOR:
            if (optval instanceof String) {
                try {
                    allocator = allocator(Class.forName((String) optval));
                    return true;
                }
                catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            else if (optval instanceof Class) {
                allocator = allocator((Class<?>) optval);
                return true;
            }
            else if (optval instanceof MsgAllocator) {
                allocator = (MsgAllocator) optval;
                return true;
            }
            return false;

        case ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD:
            Integer allocationHeapThreshold = (Integer) optval;
            allocator = new MsgAllocatorThreshold(allocationHeapThreshold);
            return true;

        case ZMQ.ZMQ_SELECTOR_PROVIDERCHOOSER:
            if (optval instanceof String) {
                try {
                    selectorChooser = chooser(Class.forName((String) optval));
                    return true;
                }
                catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            else if (optval instanceof Class) {
                selectorChooser = chooser((Class<?>) optval);
                return true;
            }
            else if (optval instanceof SelectorProviderChooser) {
                selectorChooser = (SelectorProviderChooser) optval;
                return true;
            }
            return false;

        default:
            throw new IllegalArgumentException("Unknown Option " + option);
        }
    }

    private MsgAllocator allocator(Class<?> clazz)
    {
        try {
            Class<? extends MsgAllocator> msgAllocator = clazz.asSubclass(MsgAllocator.class);
            return msgAllocator.newInstance();
        }
        catch (InstantiationException e) {
            throw new IllegalArgumentException(e);
        }
        catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private SelectorProviderChooser chooser(Class<?> clazz)
    {
        try {
            Class<? extends SelectorProviderChooser> selectorClazz = clazz.asSubclass(SelectorProviderChooser.class);
            return selectorClazz.newInstance();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new ZError.InstantiationException(e);
        }
    }

    private <T> Class<? extends T> checkCustomCodec(Object optval, Class<T> type)
    {
        Class<?> clazz = (Class<?>) optval;
        if (! type.isAssignableFrom(clazz)) {
            throw new ZError.InstantiationException("Custom " + clazz.getCanonicalName() + " is not assignable from " + type.getCanonicalName());
        }
        Class<? extends T> custom = clazz.asSubclass(type);
        try {
            custom.getConstructor(int.class, long.class);
            return custom;
        }
        catch (NoSuchMethodException | SecurityException e) {
            String message = "Custom " + clazz.getCanonicalName()
                            + " has no required constructor <init>(int bufferSize, long maxMsgSize)";
            throw new ZError.InstantiationException(message, e);
        }
    }

    private byte[] setCurveKey(int option, Object optval, ValueReference<Boolean> result)
    {
        if (optval == null) {
            // TODO V4 setting a curve key as null does change the mechanism type ?
            result.set(false);
            return null;
        }
        else {
            byte[] key = null;
            // if the optval is already the key don't do any parsing
            if (optval instanceof byte[] && ((byte[]) optval).length == CURVE_KEYSIZE) {
                key = (byte[]) optval;
                result.set(true);
                errno.set(0);
            }
            else {
                String val = parseString(option, optval);
                int length = val.length();
                if (length == CURVE_KEYSIZE_Z85) {
                    key = Z85.decode(val);
                    result.set(true);
                    errno.set(0);
                }
                else if (length == CURVE_KEYSIZE) {
                    key = val.getBytes(ZMQ.CHARSET);
                    result.set(true);
                    errno.set(0);
                }
                else {
                    result.set(false);
                    errno.set(ZError.EINVAL);
                }
            }
            if (key != null) {
                mechanism = Mechanisms.CURVE;
            }

            return key;
        }
    }

    @SuppressWarnings("deprecation")
    public Object getSocketOpt(int option)
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

        case ZMQ.ZMQ_TOS:
            return tos;

        case ZMQ.ZMQ_TYPE:
            return type;

        case ZMQ.ZMQ_LINGER:
            return linger;

        case ZMQ.ZMQ_RECONNECT_IVL:
            return reconnectIvl;

        case ZMQ.ZMQ_RECONNECT_IVL_MAX:
            return reconnectIvlMax;

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
            return !ipv6;

        case ZMQ.ZMQ_IPV6:
            return ipv6;

        case ZMQ.ZMQ_TCP_KEEPALIVE:
            return tcpKeepAlive;

        case ZMQ.ZMQ_IMMEDIATE:
            return immediate;

        case ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT:
            return !immediate;

        case ZMQ.ZMQ_SOCKS_PROXY:
            return socksProxyAddress;

        case ZMQ.ZMQ_TCP_KEEPALIVE_CNT:
        case ZMQ.ZMQ_TCP_KEEPALIVE_IDLE:
        case ZMQ.ZMQ_TCP_KEEPALIVE_INTVL:
            // not supported
            return 0;

        case ZMQ.ZMQ_MECHANISM:
            return mechanism;

        case ZMQ.ZMQ_PLAIN_SERVER:
            return asServer && mechanism == Mechanisms.PLAIN;

        case ZMQ.ZMQ_PLAIN_USERNAME:
            return plainUsername;

        case ZMQ.ZMQ_PLAIN_PASSWORD:
            return plainPassword;

        case ZMQ.ZMQ_ZAP_DOMAIN:
            return zapDomain;

        case ZMQ.ZMQ_LAST_ENDPOINT:
            return lastEndpoint;

        case ZMQ.ZMQ_CURVE_SERVER:
            return asServer && mechanism == Mechanisms.CURVE;

        case ZMQ.ZMQ_CURVE_PUBLICKEY:
            return curvePublicKey;

        case ZMQ.ZMQ_CURVE_SERVERKEY:
            return curveServerKey;

        case ZMQ.ZMQ_CURVE_SECRETKEY:
            return curveSecretKey;

        case ZMQ.ZMQ_CONFLATE:
            return conflate;

        case ZMQ.ZMQ_GSSAPI_SERVER:
            return asServer && mechanism == Mechanisms.GSSAPI;

        case ZMQ.ZMQ_GSSAPI_PRINCIPAL:
            return gssPrincipal;

        case ZMQ.ZMQ_GSSAPI_SERVICE_PRINCIPAL:
            return gssServicePrincipal;

        case ZMQ.ZMQ_GSSAPI_PLAINTEXT:
            return gssPlaintext;

        case ZMQ.ZMQ_HANDSHAKE_IVL:
            return handshakeIvl;

        case ZMQ.ZMQ_HEARTBEAT_IVL:
            return heartbeatInterval;

        case ZMQ.ZMQ_HEARTBEAT_TIMEOUT:
            return heartbeatTimeout;

        case ZMQ.ZMQ_HEARTBEAT_TTL:
            // Convert the internal deciseconds value to milliseconds
            return heartbeatTtl * 100;

        case ZMQ.ZMQ_HEARTBEAT_CONTEXT:
            return heartbeatContext;

        case ZMQ.ZMQ_MSG_ALLOCATOR:
            return allocator;

        case ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD:
            if (allocator instanceof MsgAllocatorThreshold) {
                MsgAllocatorThreshold all = (MsgAllocatorThreshold) allocator;
                return all.threshold;
            }
            return -1;

        case ZMQ.ZMQ_SELECTOR_PROVIDERCHOOSER:
            return selectorChooser;

        default:
            throw new IllegalArgumentException("option=" + option);
        }
    }

    public static boolean parseBoolean(int option, Object optval)
    {
        if (optval instanceof Boolean) {
            return (Boolean) optval;
        }
        else if (optval instanceof Integer) {
            return (Integer) optval != 0;
        }
        throw new IllegalArgumentException(optval + " is neither an integer or a boolean for option " + option);
    }

    public static String parseString(int option, Object optval)
    {
        if (optval instanceof String) {
            return (String) optval;
        }
        else if (optval instanceof byte[]) {
            return new String((byte[]) optval, ZMQ.CHARSET);
        }
        throw new IllegalArgumentException(optval + " is neither a string or an array of bytes for option " + option);
    }

    public static byte[] parseBytes(int option, Object optval)
    {
        if (optval instanceof String) {
            return ((String) optval).getBytes(ZMQ.CHARSET);
        }
        else if (optval instanceof byte[]) {
            return (byte[]) optval;
        }
        throw new IllegalArgumentException(optval + " is neither a string or an array of bytes for option " + option);
    }
}
