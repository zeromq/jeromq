package zmq.io.net.tcp;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.Assert;
import org.junit.Test;

import jdk.net.ExtendedSocketOptions;

public class KeepAliveTest
{
    @Test
    public void testConsistent() throws IOException
    {
        int javaspec = Integer.parseInt(System.getProperty("java.specification.version"));
        // Test fails for jvm less than 13
        if (javaspec >= 13) {
            SocketChannel sc = SocketChannel.open();
            TcpUtils.tuneTcpKeepalives(sc, 1, 2, 3, 4);
            Assert.assertEquals(2, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
            Assert.assertEquals(3, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
            Assert.assertEquals(4, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));

            ServerSocketChannel ssc = ServerSocketChannel.open();
            TcpUtils.tuneTcpKeepalives(ssc, 1, 2, 3, 4);
            Assert.assertEquals(2, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
            Assert.assertEquals(3, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
            Assert.assertEquals(4, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));
        }
    }
}
