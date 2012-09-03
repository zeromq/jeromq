package guide;

import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Context;
import org.jeromq.ZMQ.Socket;

/**
* Synchronized publisher.
*
* Christophe Huntzinger <chuntzin_at_wanadoo.fr>
*/
public class syncpub{
    /**
     * We wait for 10 subscribers
     */
    protected static int SUBSCRIBERS_EXPECTED = 3;

    public static void main (String[] args) {
        Context context = ZMQ.context(1);

        //  Socket to talk to clients
        Socket publisher = context.socket(ZMQ.PUB);
        publisher.setLinger(5000);
        publisher.setSndHWM(-1L); // 0MQ 3 pub socket will drop messages at SNDHWM if sub can follow the generation of pub messages
        publisher.bind("tcp://*:5561");

        //  Socket to receive signals
        Socket syncservice = context.socket(ZMQ.REP);
        syncservice.bind("tcp://*:5562");

        System.out.println("Waiting subscribers");
        //  Get synchronization from subscribers
        int subscribers = 0;
        while (subscribers < SUBSCRIBERS_EXPECTED) {
            //  - wait for synchronization request
            byte[] value = syncservice.recv(0);

            //  - send synchronization reply
            syncservice.send("".getBytes(), 0);
            subscribers++;
        }
        //  Now broadcast exactly 1M updates followed by END
        int update_nbr;
        for (update_nbr = 0; update_nbr < 1000000; update_nbr++){
            publisher.send("Rhubarb".getBytes(), 0);
        }

        publisher.send("END".getBytes(), 0);

        //  Give 0MQ/2.0.x time to flush output
        // Used linger for 0MQ/3.0
        /*try {
            Thread.sleep (1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/         

        // clean up
        publisher.close();
        syncservice.close();
        context.term();
    }
}