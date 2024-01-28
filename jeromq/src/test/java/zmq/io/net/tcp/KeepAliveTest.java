package zmq.io.net.tcp;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.Assert;
import org.junit.Test;

import zmq.Options;

public class KeepAliveTest
{
    @Test
    public void testConsistent() throws IOException
    {
        try {
            int javaspec = Integer.parseInt(System.getProperty("java.specification.version"));
            // Test fails for jvm lest that 13
            if (javaspec >= 13) {
                Class<?> eso = Options.class.getClassLoader().loadClass("jdk.net.ExtendedSocketOptions");
                SocketOption<Integer> count = (SocketOption<Integer>) eso.getField("TCP_KEEPCOUNT").get(null);
                SocketOption<Integer> idle = (SocketOption<Integer>) eso.getField("TCP_KEEPIDLE").get(null);
                SocketOption<Integer> interval = (SocketOption<Integer>) eso.getField("TCP_KEEPINTERVAL").get(null);
                Method socketgetOption = Socket.class.getMethod("getOption", SocketOption.class);
                Method serversocketgetOption = ServerSocket.class.getMethod("getOption", SocketOption.class);
                Assert.assertTrue(TcpUtils.WITH_EXTENDED_KEEPALIVE);

                SocketChannel sc = SocketChannel.open();
                TcpUtils.tuneTcpKeepalives(sc, 1, 2, 3, 4);
                Assert.assertEquals(2, socketgetOption.invoke(sc.socket(), count));
                Assert.assertEquals(3, socketgetOption.invoke(sc.socket(), idle));
                Assert.assertEquals(4, socketgetOption.invoke(sc.socket(), interval));

                ServerSocketChannel ssc = ServerSocketChannel.open();
                TcpUtils.tuneTcpKeepalives(ssc, 1, 2, 3, 4);
                Assert.assertEquals(2, serversocketgetOption.invoke(ssc.socket(), count));
                Assert.assertEquals(3, serversocketgetOption.invoke(ssc.socket(), idle));
                Assert.assertEquals(4, serversocketgetOption.invoke(ssc.socket(), interval));
            }
        }
        catch (NumberFormatException ex) {
            // java 1.8, not a problem as keepalive is not handled
        }
        catch (NoSuchMethodException | ClassNotFoundException | NoSuchFieldException | IllegalAccessException |
                 InvocationTargetException e) {
            // Not defined, just skip the test
        }

    }
}
