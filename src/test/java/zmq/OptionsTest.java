package zmq;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

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
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CONFLATE), is((Object) options.conflate));
    }

    @Test
    public void testCurvePublicKey()
    {
        byte[] key = new byte[32];
        options.setSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_PUBLICKEY), is((Object) options.curvePublicKey));
    }

    @Test
    public void testCurveSecretKey()
    {
        byte[] key = new byte[32];
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_SECRETKEY), is((Object) options.curveSecretKey));
    }

    @Test
    public void testCurveServerKey()
    {
        byte[] key = new byte[32];
        options.setSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY, key);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_CURVE_SERVERKEY), is((Object) options.curveServerKey));
    }

    @Test
    public void testGssPlaintext()
    {
        options.setSocketOpt(ZMQ.ZMQ_GSSAPI_PLAINTEXT, true);
        assertThat(options.getSocketOpt(ZMQ.ZMQ_GSSAPI_PLAINTEXT), is((Object) options.gssPlaintext));
    }

    @Test
    public void testDefaultValu()
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
    }
}
