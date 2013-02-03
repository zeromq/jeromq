package org.zeromq;

import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.junit.Test;

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
}
