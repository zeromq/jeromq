package org.zeromq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestZMQ
{
    static class Client extends Thread {

        private Socket s = null;
        public Client (Context ctx) {
            s = ctx.socket(ZMQ.PULL);
        }

        @Override
        public void run () {
            System.out.println("Start client thread ");
            s.connect( "tcp://127.0.0.1:6669");
            s.recv(0);

            s.close();
            System.out.println("Stop client thread ");
        }
    }

    @Test
    public void testPollerPollout () throws Exception
    {
        ZMQ.Context context = ZMQ.context(1);
        Client client = new Client (context);

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket (ZMQ.PUSH);
        sender.bind ("tcp://127.0.0.1:6669");

        ZMQ.Poller outItems;
        outItems = context.poller ();
        outItems.register (sender, ZMQ.Poller.POLLOUT);


        while (!Thread.currentThread ().isInterrupted ()) {

            outItems.poll (1000);
            if (outItems.pollout (0)) {
                sender.send ("OK", 0);
                System.out.println ("ok");
                break;
            } else {
                System.out.println ("not writable");
                client.start ();
            }
        }
        client.join ();
        sender.close ();
        context.term ();
    }
    
    @Test
    public void testByteBufferSend() throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:12344");
            push.connect("tcp://localhost:12344");
            bb.put("PING".getBytes(ZMQ.CHARSET));
            bb.flip();
            push.sendByteBuffer(bb, 0);
            String actual = new String(pull.recv(), ZMQ.CHARSET);
            assertEquals("PING", actual);
        } finally {
            try {
                push.close();
            } catch (Exception ignore) {
            }
            try {
                pull.close();
            } catch (Exception ignore) {
            }
            try {
                context.term();
            } catch (Exception ignore) {
            }
        }

    }

    @Test
    public void testByteBufferRecv() throws InterruptedException, CharacterCodingException {
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:12345");
            push.connect("tcp://localhost:12345");
            push.send("PING".getBytes(ZMQ.CHARSET), 0);
            pull.recvByteBuffer(bb, 0);
            bb.flip();
            byte[] b = new byte[bb.remaining()];
            bb.duplicate().get(b);
            assertEquals("PING", new String(b, ZMQ.CHARSET));
        } finally {
            try {
                push.close();
            } catch (Exception ignore) {
            }
            try {
                pull.close();
            } catch (Exception ignore) {
            }
            try {
                context.term();
            } catch (Exception ignore) {
            }
        }

    }

    @Test(expected = ZMQException.class)
    public void testBindSameAddress()
    {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(ZMQ.REQ);
        ZMQ.Socket socket2 = context.socket(ZMQ.REQ);
        socket1.bind("tcp://*:12346");
        try
        {
            socket2.bind("tcp://*:12346");
            fail("Exception not thrown");
        } catch (ZMQException e)
        {
            assertEquals(e.getErrorCode(), ZMQ.Error.EADDRINUSE.getCode());
            throw e;
        } finally {
            socket1.close();
            socket2.close();

            context.term();
        }
    }
}
