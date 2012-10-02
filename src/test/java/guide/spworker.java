package guide;

import java.util.Random;

import org.jeromq.ZContext;
import org.jeromq.ZFrame;
import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Socket;
import org.jeromq.ZMsg;

//
// Simple Pirate worker
// Connects REQ socket to tcp://*:5556
// Implements worker part of LRU queueing
//
public class spworker {

    private final static String LRU_READY  = "\001";      //  Signals worker is ready

    public static void main(String[] args) {
        ZContext ctx = new ZContext ();
        Socket worker = ctx.createSocket(ZMQ.REQ);

        //  Set random identity to make tracing easier
        Random rand = new Random(System.nanoTime());
        String identity = String.format("%04X-%04X", rand.nextInt (0x10000), rand.nextInt (0x10000));
        worker.setIdentity(identity); 
        worker.connect( "tcp://localhost:5556");

        //  Tell broker we're ready for work
        System.out.println (String.format("I: (%s) worker ready\n", identity));
        ZFrame frame = new ZFrame (LRU_READY);
        frame.send(worker, 0);

        int cycles = 0;
        while (true) {
            ZMsg msg = ZMsg.recvMsg (worker);
            if (msg == null)
                break;              //  Interrupted

            //  Simulate various problems, after a few cycles
            cycles++;
            if (cycles > 3 && rand.nextInt (5) == 0) {
                System.out.println (String.format("I: (%s) simulating a crash\n", identity));
                msg.destroy();
                break;
            }
            else
            if (cycles > 3 && rand.nextInt (5) == 0) {
                System.out.println (String.format("I: (%s) simulating CPU overload\n", identity));
                try {
                    Thread.sleep (3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println (String.format("I: (%s) normal reply\n", identity));
            try {
                Thread.sleep (1000);
            } catch (InterruptedException e) {
            }              //  Do some heavy work
            msg.send( worker);
        }
        ctx.destroy();
    }

}
