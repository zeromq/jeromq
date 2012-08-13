package zmq;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestUtil {

    public static AtomicInteger counter = new AtomicInteger(2);
    
    public static class DummyCtx extends Ctx {
        
    }
    
    public static DummyCtx CTX = new DummyCtx();
    
    public static class DummyIOThread extends IOThread {

        public DummyIOThread() {
            super(CTX, 2);
        }
    }
    
    public static class DummySocket extends SocketBase {

        public DummySocket() {
            super(CTX, counter.get(), counter.get());
            counter.incrementAndGet();
        }
        
    }
    
    public static class DummySession extends SessionBase {

        public List<Msg> out = new ArrayList<Msg>();
        
        public DummySession () {
            this(new DummyIOThread(),  false, new DummySocket(), new Options(), new Address("tcp", "localhost:9090"));
        }
        
        public DummySession(IOThread io_thread_, boolean connect_,
                SocketBase socket_, Options options_, Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }
        
        @Override
        public boolean write(Msg msg) {
            System.out.println("session.write " + msg);
            out.add(msg);
            return true;
        }
        
    }
    
    public static void bounce (SocketBase sb, SocketBase sc)
    {
        byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes();

        //  Send the message.
        int rc = ZMQ.zmq_send (sc, content, 32, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.zmq_send (sc, content, 32, 0);
        assertThat (rc ,is( 32));

        //  Bounce the message back.
        Msg msg;
        msg = ZMQ.zmq_recv (sb, 0);
        assert (msg.size() == 32);
        int rcvmore = ZMQ.zmq_getsockopt (sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 1);
        msg = ZMQ.zmq_recv (sb, 0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt (sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 0);
        rc = ZMQ.zmq_send (sb, msg, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.zmq_send (sb, msg, 0);
        assert (rc == 32);

        //  Receive the bounced message.
        msg = ZMQ.zmq_recv (sc, 0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt (sc, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 1);
        msg = ZMQ.zmq_recv (sc,  0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt (sc, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 0);
        //  Check whether the message is still the same.
        //assert (memcmp (buf2, content, 32) == 0);
    }
    
    public static void send (Socket sa, String data) throws IOException
    {
        byte[] content = data.getBytes();

        byte[] length = String.format("%04d", content.length).getBytes();

        byte[] buf = new byte[1024];
        int reslen ;
        int rc;
        //  Bounce the message back.
        InputStream in = sa.getInputStream();
        OutputStream out = sa.getOutputStream();

        out.write(length);
        out.write(content);
        
        System.out.println("sent " + data.length() + " " + data);
        rc = in.read(buf, 0, 4);
        assertThat (rc, is( 4));
        System.out.println(String.format("%02x %02x", buf[0], buf[1]));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        reslen = Integer.valueOf(new String(buf, 0, 4));

        in.read(buf, 0, reslen);
        System.out.println("recv " + reslen + " " + new String(buf,0, reslen));
        
    }

}
