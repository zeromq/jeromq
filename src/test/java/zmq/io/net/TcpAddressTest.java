package zmq.io.net;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.Test;

import zmq.io.net.tcp.TcpAddress;
import zmq.util.Utils;

public class TcpAddressTest
{
    @Test
    public void parsesIpv6Address() throws IOException
    {
        String addressString = "2000::a1";
        int port = Utils.findOpenPort();
        TcpAddress address = new TcpAddress("[" + addressString + "]:" + port, true);

        InetSocketAddress expected = new InetSocketAddress(addressString, port);
        assertEquals(expected, address.address());
    }
}
