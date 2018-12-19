package zmq.faultinjection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class FaultInjectionTest {

    ZMTP3Server testServer;
    ZmqServerEvents events;
    ZContext ctx = new ZContext();
    ZMQ.Socket brokerSocket;

    @Before
    public void setup() throws IOException
    {
        testServer = new ZMTP3Server();
        testServer.initialize(new InetSocketAddress(4200));
        events = new ZmqServerEvents(testServer);
        testServer.setDelegate(events);

        brokerSocket = ctx.createSocket(ZMQ.DEALER);
        brokerSocket.setHWM(10);
        brokerSocket.setSendTimeOut(-1);
        brokerSocket.setDelayAttachOnConnect(true);
        brokerSocket.setTCPKeepAlive(1);

        brokerSocket.connect("tcp://localhost:4200");

    }

    @After
    public void cleanup() {
        try {
            testServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStart()
    {
        boolean debugger_attached = false;

        StringBuilder sb = new StringBuilder(5);
        for (int i =0; i < 5; i++ ) {
            sb.append(i%10);
        }

        boolean cont = true;

        for (int i = 0; i < 10; i ++ ) {
            brokerSocket.send(sb.toString());
            sleep(100);
        }

        if (debugger_attached) {
            sleep(-1);
        } else {
            sleep(5000);
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class ZmqServerEvents implements ZMTP3Server.ISocketEvents {

        ZMTP3Server testServer;

        public ZmqServerEvents(ZMTP3Server server) {
            testServer = server;
        }

        long start = System.currentTimeMillis();

        @Override
        public void onConnectionReceived(SocketChannel channel) {

        }

        @Override
        public void onGreetingReceived(SocketChannel channel) {
            try {
                channel.close();
                testServer.stop();
            } catch (IOException e) {

            }


        }

        @Override
        public void onHandshakeReceived(SocketChannel channel) {

        }

        @Override
        public void onCommandReceived(SocketChannel channel, String command) {

        }

        @Override
        public void onMessageReceived(SocketChannel channel, String message) {

            if (System.currentTimeMillis() - start < 30000) {
                return;
            }

            try {
                channel.socket().shutdownInput();
            } catch (IOException e) {

            }

            // reset start.
            start = System.currentTimeMillis();

        }
    }
}
