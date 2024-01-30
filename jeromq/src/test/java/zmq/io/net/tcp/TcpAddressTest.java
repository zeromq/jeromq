package zmq.io.net.tcp;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQException;

import zmq.ZError;
import zmq.io.net.Address;
import zmq.io.net.NetProtocol;
import zmq.util.Utils;

public class TcpAddressTest
{
    @Test
    public void parsesIpv6AddressSimple() throws IOException
    {
        String addressString = "2000::a1";
        int port = Utils.findOpenPort();
        Address addr = new Address(NetProtocol.tcp, addressString + ":" + port);
        addr.resolve(true);
        InetSocketAddress expected = new InetSocketAddress(addressString, port);
        Address.IZAddress resolved = addr.resolved();
        assertEquals(expected, resolved.address());
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
        Assert.assertEquals(port, sa.getPort());
    }

    @Test
    public void parsesIpv6AddressBracket() throws IOException
    {
        String addressString = "2000::a1";
        int port = Utils.findOpenPort();
        Address addr = new Address(NetProtocol.tcp, "[" + addressString + "]:" + port);
        addr.resolve(true);
        InetSocketAddress expected = new InetSocketAddress(addressString, port);
        Address.IZAddress resolved = addr.resolved();
        assertEquals(expected, resolved.address());
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
        Assert.assertEquals(port, sa.getPort());
    }

    @Test
    public void parsesIpv6AddressNotWanted() throws IOException
    {
        try {
            String addressString = "2000::a1";
            int port = Utils.findOpenPort();
            Address addr = new Address(NetProtocol.tcp,
                                       addressString + ":" + port);
            addr.resolve(false);
            InetSocketAddress expected = new InetSocketAddress(addressString,
                                                               port);
            Address.IZAddress resolved = addr.resolved();
            assertEquals(expected, resolved.address());
            InetSocketAddress sa = (InetSocketAddress) resolved.address();
            Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
            Assert.assertEquals(port, sa.getPort());
            Assert.fail();
        }
        catch (ZMQException e) {
            Assert.assertEquals(ZError.EADDRNOTAVAIL, e.getErrorCode());
            Assert.assertEquals("2000::a1 not found matching IPv4/IPv6 settings", e.getMessage());
        }
    }

    @Ignore // Fails on both Circleci and Travis
    @Test
    public void testGoodIPv6Google()
    {
        Address addr = new Address(NetProtocol.tcp, "www.google.com:80");
        addr.resolve(true);
        Address.IZAddress resolved = addr.resolved();
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
        Assert.assertEquals(80, sa.getPort());
    }

    @Test
    public void testGoodIP46Google()
    {
        Address addr = new Address(NetProtocol.tcp, "www.google.com:80");
        addr.resolve(false);
        Address.IZAddress resolved = addr.resolved();
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet4Address);
        Assert.assertEquals(80, sa.getPort());
    }

    @Test
    public void testBad()
    {
        try {
            Address addr = new Address(NetProtocol.tcp, "ggglocalhostxxx.google.com:80");
            addr.resolve(true);
            addr.resolved();
            Assert.fail();
        }
        catch (ZMQException e) {
            Assert.assertEquals(ZError.EADDRNOTAVAIL, e.getErrorCode());
            Assert.assertEquals(e.getCause().getMessage(), e.getMessage());
        }
    }

    @Test
    public void testUnspecifiedIPv6DoubleColon() throws IOException
    {
        int port = Utils.findOpenPort();
        Address addr = new Address(NetProtocol.tcp, ":::" + port);
        addr.resolve(true);
        Address.IZAddress resolved = addr.resolved();
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
        String hostString = sa.getHostString();
        Assert.assertTrue("::".equals(hostString) || "0:0:0:0:0:0:0:0".equals(hostString));
        Assert.assertEquals(port, sa.getPort());
    }

    @Test
    public void testUnspecifiedIPv6Star() throws IOException
    {
        int port = Utils.findOpenPort();
        Address addr = new Address(NetProtocol.tcp, "*:" + port);
        addr.resolve(true);
        Address.IZAddress resolved = addr.resolved();
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet6Address);
        String hostString = sa.getHostString();
        Assert.assertTrue("::".equals(hostString) || "0:0:0:0:0:0:0:0".equals(hostString));
        Assert.assertEquals(port, sa.getPort());
    }

    @Test
    public void testUnspecifiedIPv4() throws IOException
    {
        int port = Utils.findOpenPort();
        Address addr = new Address(NetProtocol.tcp, "*:" + port);
        addr.resolve(false);
        Address.IZAddress resolved = addr.resolved();
        InetSocketAddress sa = (InetSocketAddress) resolved.address();
        Assert.assertTrue(sa.getAddress() instanceof Inet4Address);
        Assert.assertEquals("0.0.0.0", sa.getHostString());
        Assert.assertEquals(port, sa.getPort());
    }
}
