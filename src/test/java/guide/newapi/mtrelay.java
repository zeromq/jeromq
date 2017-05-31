package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Multi-threaded relay
 */
public class mtrelay {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  Bind to inproc: endpoint, then start upstream thread
        Socket receiver = context.createSocket(SocketType.PAIR);
        receiver.bind("inproc://step3");

        //  Step 2 relays the signal to step 3
        Thread step2 = new Thread(new Step2(context));
        step2.start();

        //  Wait for signal
        receiver.receive();
        receiver.destroy();

        System.out.println("Step 3 says: Test successful!");
        context.terminate();
    }

    private static class Step1 implements Runnable {

        private final ZeroMQContext context;

        private Step1(ZeroMQContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            //  Signal downstream to step 2
            Socket transmitter = context.createSocket(SocketType.PAIR);
            transmitter.connect("inproc://step2");
            System.out.println("Step 1 ready, signaling step 2");
            transmitter.send("READY");
            transmitter.destroy();
        }

    }

    private static class Step2 implements Runnable {

        private final ZeroMQContext context;

        private Step2(ZeroMQContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            //  Bind to inproc: endpoint, then start upstream thread
            Socket receiver = context.createSocket(SocketType.PAIR);
            receiver.bind("inproc://step2");

            Thread step1 = new Thread(new Step1(context));
            step1.start();

            //  Wait for signal
            receiver.receive();
            receiver.destroy();
            System.out.println("Step 2 complete, signalling Step 3");

            //  Connect to step3 and tell it we're ready
            Socket transmitter = context.createSocket(SocketType.PAIR);
            transmitter.connect("inproc://step3");
            transmitter.send("READY");

            transmitter.destroy();
        }

    }
}
