package zmq.io;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class MetadataTest
{
    private static class ZapHandler implements Runnable
    {
        private final SocketBase handler;

        public ZapHandler(SocketBase handler)
        {
            this.handler = handler;
        }

        @SuppressWarnings("unused")
        @Override
        public void run()
        {
            byte[] metadata = { 5, 'H', 'e', 'l', 'l', 'o', 0, 0, 0, 5, 'W', 'o', 'r', 'l', 'd' };

            //  Process ZAP requests forever
            while (true) {
                Msg version = ZMQ.recv(handler, 0);
                if (version == null) {
                    break; //  Terminating
                }
                Msg sequence = ZMQ.recv(handler, 0);
                Msg domain = ZMQ.recv(handler, 0);
                Msg address = ZMQ.recv(handler, 0);
                Msg identity = ZMQ.recv(handler, 0);
                Msg mechanism = ZMQ.recv(handler, 0);

                assertThat(new String(version.data(), ZMQ.CHARSET), is("1.0"));
                assertThat(new String(mechanism.data(), ZMQ.CHARSET), is("NULL"));

                int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(3));
                ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                assertThat(ret, is(1));

                System.out.println("Sending ZAP reply");
                if ("DOMAIN".equals(new String(domain.data(), ZMQ.CHARSET))) {
                    ret = ZMQ.send(handler, "200", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    ret = ZMQ.send(handler, "OK", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(2));
                    ret = ZMQ.send(handler, "anonymous", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(9));
                    ret = ZMQ.send(handler, metadata, metadata.length, 0);
                    assertThat(ret, is(metadata.length));
                }
                else {
                    ret = ZMQ.send(handler, "400", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(3));
                    ret = ZMQ.send(handler, "BAD DOMAIN", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(10));
                    ret = ZMQ.send(handler, "", ZMQ.ZMQ_SNDMORE);
                    assertThat(ret, is(0));
                    ret = ZMQ.send(handler, "", 0);
                    assertThat(ret, is(0));
                }
            }
            ZMQ.closeZeroLinger(handler);
        }
    }

    @Test
    public void testMetadata() throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        Ctx ctx = ZMQ.createContext();

        //  Spawn ZAP handler
        //  We create and bind ZAP socket in main thread to avoid case
        //  where child thread does not start up fast enough.
        SocketBase handler = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(handler, notNullValue());
        boolean rc = ZMQ.bind(handler, "inproc://zeromq.zap.01");
        assertThat(rc, is(true));

        Thread thread = new Thread(new ZapHandler(handler));
        thread.start();

        //  Server socket will accept connections
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());
        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        ZMQ.setSocketOption(server, ZMQ.ZMQ_ZAP_DOMAIN, "DOMAIN");

        rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        int ret = ZMQ.send(client, "This is a message", 0);
        assertThat(ret, is(17));

        Msg msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());

        String prop = ZMQ.getMessageMetadata(msg, "Socket-Type");
        assertThat(prop, is("DEALER"));

        prop = ZMQ.getMessageMetadata(msg, "User-Id");
        assertThat(prop, is("anonymous"));

        prop = ZMQ.getMessageMetadata(msg, "Peer-Address");
        assertThat(prop.startsWith("127.0.0.1:"), is(true));

        prop = ZMQ.getMessageMetadata(msg, "no such");
        assertThat(prop, nullValue());

        prop = ZMQ.getMessageMetadata(msg, "Hello");
        assertThat(prop, is("World"));

        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        //  Shutdown
        ZMQ.term(ctx);
        //  Wait until ZAP handler terminates
        thread.join();
    }
}
