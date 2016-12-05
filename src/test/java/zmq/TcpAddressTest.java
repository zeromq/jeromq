package zmq;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

public class TcpAddressTest
{
    @Test
    public void parsesIpv6Address() throws IOException
    {
        String addressString = "2000::a1";
        int port = Utils.findOpenPort();
        TcpAddress address = new TcpAddress("[" + addressString + "]:" + port);

        InetSocketAddress expected = new InetSocketAddress(addressString, port);
        assertEquals(expected, address.address());
    }
}
