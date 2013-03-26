package guide;

//
//Custom routing Router to Papa (ROUTER to REP)
//

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class rtpapa
{

    //  We will do this all in one thread to emphasize the sequence
    //  of events
    public static void main(String[] args)
    {
        Context context = ZMQ.context(1);

        Socket client = context.socket(ZMQ.ROUTER);
        client.bind("ipc://routing.ipc");

        Socket worker = context.socket(ZMQ.REP);
        worker.setIdentity("A".getBytes());
        worker.connect("ipc://routing.ipc");

        //  Wait for the worker to connect so that when we send a message
        //  with routing envelope, it will actually match the worker
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //  Send papa address, address stack, empty part, and request
        client.send("A", ZMQ.SNDMORE);
        client.send("address 3", ZMQ.SNDMORE);
        client.send("address 2", ZMQ.SNDMORE);
        client.send("address 1", ZMQ.SNDMORE);
        client.send("", ZMQ.SNDMORE);
        client.send("This is the workload", 0);

        //  Worker should get just the workload
        ZHelper.dump(worker);

        //  We don't play with envelopes in the worker
        worker.send("This is the reply", 0);

        //  Now dump what we got off the ROUTER socket
        ZHelper.dump(client);

        client.close();
        worker.close();
        context.term();

    }

}
