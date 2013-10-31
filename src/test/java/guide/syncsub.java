package guide;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
* Synchronized subscriber.
*/
public class syncsub{

    public static void main (String[] args) {
        Context context = ZMQ.context(1);

        //  First, connect our subscriber socket
        Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5561");
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

        //  Second, synchronize with publisher
        Socket syncclient = context.socket(ZMQ.REQ);
        syncclient.connect("tcp://localhost:5562");

        //  - send a synchronization request
        syncclient.send(ZMQ.MESSAGE_SEPARATOR, 0);

        //  - wait for synchronization reply
        syncclient.recv(0);

        //  Third, get our updates and report how many we got
        int update_nbr = 0;
        while (true) {
            String string = subscriber.recvStr(0);
            if (string.equals("END")) {
                break;
            }
            update_nbr++;
        }
        System.out.println("Received " + update_nbr + " updates.");

        subscriber.close();
        syncclient.close();
        context.term();
    }
}