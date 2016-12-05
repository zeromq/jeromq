package zmq;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestProxyTcp
{
    static class Client extends Thread
    {
        private int port;

        public Client(int port)
        {
            this.port = port;
        }

        @Override
        public void run()
        {
            System.out.println("Start client thread");
            try {
                Socket s = new Socket("127.0.0.1", port);
                Helper.send(s, "hellow");
                Helper.send(s, "1234567890abcdefghizklmnopqrstuvwxyz");
                Helper.send(s, "end");
                Helper.send(s, "end");
                s.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Stop client thread");
        }
    }

    static class Dealer extends Thread
    {
        private final SocketBase s;
        private final String name;
        private final int port;

        public Dealer(Ctx ctx, String name, int port)
        {
            this.s = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            this.name = name;
            this.port = port;
        }

        @Override
        public void run()
        {
            System.out.println("Start dealer " + name);

            ZMQ.connect(s, "tcp://127.0.0.1:" + port);
            int i = 0;
            while (true) {
                Msg msg = s.recv(0);
                if (msg == null) {
                    throw new RuntimeException("hello");
                }
                System.out.println("REP recieved " + msg);
                String data = new String(msg.data(), 0, msg.size(), ZMQ.CHARSET);

                Msg response = null;
                if ((i % 3) == 2) {
                    response = new Msg(msg.size() + 3);
                    response.put("OK ".getBytes(ZMQ.CHARSET))
                            .put(msg.data());
                }
                else {
                    response = new Msg(msg.data());
                }

                s.send(response, (i % 3) == 2 ? 0 : ZMQ.ZMQ_SNDMORE);
                i++;
                if (data.equals("end")) {
                    break;
                }
            }
            s.close();
            System.out.println("Stop dealer " + name);
        }
    }

    static class ProxyDecoder extends DecoderBase
    {
        private static final int READ_HEADER = 0;
        private static final int READ_BODY = 1;

        byte [] header = new byte [4];
        Msg msg;
        int size = -1;
        boolean identitySent = false;
        Msg bottom;
        IMsgSink msgSink;

        public ProxyDecoder(int bufsize, long maxmsgsize)
        {
            super(bufsize);
            nextStep(header, 4, READ_HEADER);

            bottom = new Msg();
            bottom.setFlags(Msg.MORE);
        }

        @Override
        protected boolean next()
        {
            switch (state()) {
            case READ_HEADER:
                return readHeader();
            case READ_BODY:
                return readBody();
            }
            return false;
        }

        private boolean readHeader()
        {
            size = Integer.parseInt(new String(header, ZMQ.CHARSET));
            System.out.println("Received " + size);
            msg = new Msg(size);
            nextStep(msg, READ_BODY);

            return true;
        }

        private boolean readBody()
        {
            if (msgSink == null) {
                return false;
            }

            System.out.println("Received body " + new String(msg.data(), ZMQ.CHARSET));

            if (!identitySent) {
                Msg identity = new Msg();
                msgSink.pushMsg(identity);
                identitySent = true;
            }

            msgSink.pushMsg(bottom);
            msgSink.pushMsg(msg);

            nextStep(header, 4, READ_HEADER);
            return true;
        }

        @Override
        public boolean stalled()
        {
            return state() == READ_BODY;
        }

        @Override
        public void setMsgSink(IMsgSink msgSink)
        {
            this.msgSink = msgSink;
        }
    }

    static class ProxyEncoder extends EncoderBase
    {
        public static final boolean RAW_ENCODER = true;
        private static final int WRITE_HEADER = 0;
        private static final int WRITE_BODY = 1;

        ByteBuffer header = ByteBuffer.allocate(4);
        Msg msg;
        int size = -1;
        boolean messageReady;
        boolean identityRecieved;
        IMsgSource msgSource;

        public ProxyEncoder(int bufsize)
        {
            super(bufsize);
            nextStep((Msg) null, WRITE_HEADER, true);
            messageReady = false;
            identityRecieved = false;
        }

        @Override
        protected boolean next()
        {
            switch (state()) {
            case WRITE_HEADER:
                return writeHeader();
            case WRITE_BODY:
                return writeBody();
            }
            return false;
        }

        private boolean writeBody()
        {
            System.out.println("writer body ");
            nextStep(msg, WRITE_HEADER, !msg.hasMore());

            return true;
        }

        private boolean writeHeader()
        {
            if (msgSource == null) {
                return false;
            }

            msg = msgSource.pullMsg();

            if (msg == null) {
                return false;
            }
            if (!identityRecieved) {
                identityRecieved = true;
                nextStep(header.array(), msg.size() < 255 ? 2 : 10, WRITE_BODY, false);
                return true;
            }
            else
            if (!messageReady) {
                messageReady = true;
                msg = msgSource.pullMsg();

                if (msg == null) {
                    return false;
                }
            }
            messageReady = false;
            System.out.println("write header " + msg.size());

            header.clear();
            header.put(String.format("%04d", msg.size()).getBytes(ZMQ.CHARSET));
            header.flip();
            nextStep(header.array(), 4, WRITE_BODY, false);
            return true;
        }

        @Override
        public void setMsgSource(IMsgSource msgSource)
        {
            this.msgSource = msgSource;
        }
    }

    static class Main extends Thread
    {
        private Ctx ctx;
        private int routerPort;
        private int dealerPort;

        Main(Ctx ctx, int routerPort, int dealerPort)
        {
            this.ctx = ctx;
            this.routerPort = routerPort;
            this.dealerPort = dealerPort;
        }

        @Override
        public void run()
        {
            boolean rc;
            SocketBase sa = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
            assertThat(sa, notNullValue());

            sa.setSocketOpt(ZMQ.ZMQ_DECODER, ProxyDecoder.class);
            sa.setSocketOpt(ZMQ.ZMQ_ENCODER, ProxyEncoder.class);

            rc = ZMQ.bind(sa, "tcp://127.0.0.1:" + routerPort);
            assertThat(rc, is(true));

            SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            assertThat(sb, notNullValue());
            rc = ZMQ.bind(sb, "tcp://127.0.0.1:" + dealerPort);
            assertThat(rc, is(true));

            ZMQ.proxy(sa, sb, null);

            ZMQ.close(sa);
            ZMQ.close(sb);
        }
    }

    @Test
    public void testProxyTcp() throws Exception
    {
        int routerPort = Utils.findOpenPort();
        int dealerPort = Utils.findOpenPort();

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        Main mt = new Main(ctx, routerPort, dealerPort);
        mt.start();

        new Dealer(ctx, "A", dealerPort).start();
        new Dealer(ctx, "B", dealerPort).start();

        Thread.sleep(1000);
        Thread client = new Client(routerPort);
        client.start();

        client.join();

        ZMQ.term(ctx);
    }
}
