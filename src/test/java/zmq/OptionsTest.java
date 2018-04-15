package zmq;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import zmq.io.mechanism.Mechanisms;
import zmq.msg.MsgAllocatorDirect;
import zmq.msg.MsgAllocatorThreshold;

public class OptionsTest
{
    private Options options;

    @Before
    public void setUp()
    {
        options = new Options();
    }

    @Test
    public void testDefaultValues()
    {
        assertThat(options.affinity, is(0L));
        assertThat(options.allocator, notNullValue());
        assertThat(options.allocator, is(instanceOf(MsgAllocatorThreshold.class)));
        assertThat(options.backlog, is(100));
        assertThat(options.conflate, is(false));
    }

    @Test
    public void testAffinity()
    {
        options.setSocketOpt(ZMQ.ZMQ_AFFINITY, 1000L);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_AFFINITY), is((Object) 1000L));
    }

    @Test
    public void testAllocator()
    {
        options.setSocketOpt(ZMQ.ZMQ_MSG_ALLOCATOR, new MsgAllocatorDirect());
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MSG_ALLOCATOR), is((Object) options.allocator));
    }

    @Test
    public void testBacklog()
    {
        options.setSocketOpt(ZMQ.ZMQ_BACKLOG, 2000);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_BACKLOG), is((Object) 2000));
    }

    @Test
    public void testConflate()
    {
        options.setSocketOpt(ZMQ.ZMQ_CONFLATE, true);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CONFLATE), is((Object) true));
    }

    @Test
    public void testRate()
    {
        options.setSocketOpt(ZMQ.ZMQ_RATE, 10);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RATE), is((Object) 10));
    }

    @Test
    public void testRecoveryIvl()
    {
        options.setSocketOpt(ZMQ.ZMQ_RECOVERY_IVL, 11);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RECOVERY_IVL), is((Object) 11));
    }

    @Test
    public void testMulticastHops()
    {
        options.setSocketOpt(ZMQ.ZMQ_MULTICAST_HOPS, 12);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MULTICAST_HOPS), is((Object) 12));
    }

    @Test
    public void testPlainUsername()
    {
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVER, true);
        String username = "username";

        options.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, username);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME), is((Object) username));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_SERVER), is((Object) false));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MECHANISM), is((Object) Mechanisms.PLAIN));
    }

    @Test
    public void testPlainPassword()
    {
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVER, true);
        String password = "password";

        options.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, password);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD), is((Object) password));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_SERVER), is((Object) false));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MECHANISM), is((Object) Mechanisms.PLAIN));
    }

    @Test
    public void testPlainUsernameNull()
    {
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVER, true);

        options.setSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME, null);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME), nullValue());
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_SERVER), is((Object) false));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MECHANISM), is((Object) Mechanisms.NULL));
    }

    @Test
    public void testPlainPasswordNull()
    {
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVER, true);

        options.setSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD, null);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD), nullValue());
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_SERVER), is((Object) false));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MECHANISM), is((Object) Mechanisms.NULL));
    }

    @Test
    public void testCurvePublicKey()
    {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 11);
        options.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY), is((Object) key));
    }

    @Test
    public void testCurveSecretKey()
    {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 12);
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY), is((Object) key));
    }

    @Test
    public void testCurveServerKey()
    {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 14);
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY), is((Object) key));
    }

    @Test
    public void testGssPlaintext()
    {
        options.setSocketOpt(ZMQ.ZMQ_GSSAPI_PLAINTEXT, true);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_GSSAPI_PLAINTEXT), is((Object) true));
    }

    @Test
    public void testHeartbeatInterval()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_IVL, 1000);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_IVL), is((Object) 1000));
    }

    @Test
    public void testHeartbeatTimeout()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TIMEOUT, 1001);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TIMEOUT), is((Object) 1001));
    }

    @Test
    public void testHeartbeatTtlRounded()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, 2020);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL), is((Object) 2000));
    }

    @Test
    public void testHeartbeatTtlMin()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, -99);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL), is((Object) 0));
    }

    @Test
    public void testHeartbeatTtlRoundedMin()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, 99);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL), is((Object) 0));
    }

    @Test
    public void testHeartbeatTtlMax()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, 655399);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL), is((Object) 655300));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHeartbeatTtlOverflow()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, 655400);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHeartbeatTtlUnderflow()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL, -100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHeartbeatIvlUnderflow()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_IVL, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHeartbeatTimeoutUnderflow()
    {
        options.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_TIMEOUT, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHandshakeIvlUnderflow()
    {
        options.setSocketOpt(ZMQ.ZMQ_HANDSHAKE_IVL, -1);
    }

    @Test
    public void testDefaultValue()
    {
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_DECODER), is((Object)options.decoder));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_ENCODER), is((Object)options.encoder));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_GSSAPI_PRINCIPAL), is((Object) options.gssPrincipal));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_GSSAPI_SERVICE_PRINCIPAL), is((Object) options.gssServicePrincipal));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HANDSHAKE_IVL), is((Object) options.handshakeIvl));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_IDENTITY), is((Object) options.identity));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_IMMEDIATE), is((Object) options.immediate));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_ACCEPT_FILTER), is((Object)options.ipcAcceptFilters));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_IPV6), is((Object) options.ipv6));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_LAST_ENDPOINT), is((Object) options.lastEndpoint));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_LINGER), is((Object) options.linger));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MAXMSGSIZE), is((Object) options.maxMsgSize));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MECHANISM), is((Object) options.mechanism));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_MULTICAST_HOPS), is((Object) options.multicastHops));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_PASSWORD), is((Object) options.plainPassword));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_PLAIN_USERNAME), is((Object) options.plainUsername));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RATE), is((Object) options.rate));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RCVBUF), is((Object) options.rcvbuf));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RECONNECT_IVL), is((Object) options.reconnectIvl));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RECONNECT_IVL_MAX), is((Object) options.reconnectIvlMax));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RECOVERY_IVL), is((Object) options.recoveryIvl));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RCVHWM), is((Object) options.recvHwm));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_RCVTIMEO), is((Object) options.recvTimeout));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_SNDHWM), is((Object) options.sendHwm));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_SNDTIMEO), is((Object) options.sendTimeout));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_SNDBUF), is((Object) options.sndbuf));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_SOCKS_PROXY), is((Object) options.socksProxyAddress));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_ACCEPT_FILTER), is((Object)options.tcpAcceptFilters));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_KEEPALIVE), is((Object) options.tcpKeepAlive));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_KEEPALIVE_CNT), is((Object)options.tcpKeepAliveCnt));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_KEEPALIVE_IDLE), is((Object)options.tcpKeepAliveIdle));
        //        assertThat(options.getSocketOpt(ZMQ.ZMQ_TCP_KEEPALIVE_INTVL), is((Object)options.tcpKeepAliveIntvl));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_TOS), is((Object) options.tos));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_TYPE), is((Object) options.type));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_ZAP_DOMAIN), is((Object) options.zapDomain));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HANDSHAKE_IVL), is((Object) options.handshakeIvl));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_IVL), is((Object) options.heartbeatInterval));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TIMEOUT), is((Object) options.heartbeatTimeout));
        assertThat(options.getSocketOpt(ZMQ.ZMQ_HEARTBEAT_TTL), is((Object) options.heartbeatTtl));
    }
}
