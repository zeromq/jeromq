package guide;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Poller;
import org.jeromq.ZMQ.Socket;
import org.jeromq.ZContext;
import org.jeromq.ZFrame;
import org.jeromq.ZMsg;

class ClientThread2 extends Thread
{
    public void run()
    {
        ZContext context = new ZContext();

        //  Prepare our context and sockets
        Socket client  = context.createSocket(ZMQ.REQ);

        //  Initialize random number generator
        client.connect("ipc://frontend.ipc");

        //  Send request, get reply
        while (true) {
            client.send("HELLO".getBytes(), 0);
            byte[] data = client.recv(0);
            
            if (data == null)
                break;
            String reply = new String(data);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            
            System.out.println(Thread.currentThread().getName() + " Client Sent HELLO");

        }
        context.destroy();
    }
}

class WorkerThread2 extends Thread
{
    
    public void run()
    {
        ZContext context = new ZContext();
        //  Prepare our context and sockets
        Socket worker  = context.createSocket(ZMQ.REQ);

        worker.connect("ipc://backend.ipc");

        ZFrame frame = new ZFrame(lruqueue2.LRU_READY);
        //  Tell backend we're ready for work
        frame.send(worker, 0);

        while(true)
        {
            ZMsg msg = ZMsg.recvMsg(worker);
            if (msg == null)
                break;

            msg.getLast().reset("OK".getBytes());
            
            msg.send(worker);
            System.out.println(Thread.currentThread().getName() + " Worker Sent OK");
        }

        context.destroy();
    }
}

public class lruqueue2 {

    public final static String LRU_READY = "\001";
    
    public static void main(String[] args) {
        ZContext context = new ZContext();
        
        //  Prepare our context and sockets
        Socket frontend  = context.createSocket(ZMQ.ROUTER);
        Socket backend  = context.createSocket(ZMQ.ROUTER);
        context.getContext().log();
        frontend.bind("ipc://frontend.ipc");
        backend.bind("ipc://backend.ipc");

        int client_nbr;
        for (client_nbr = 0; client_nbr < 10; client_nbr++)
            new ClientThread2().start();

        int worker_nbr;
        for (worker_nbr = 0; worker_nbr < 3; worker_nbr++)
            new WorkerThread2().start();

        //  Logic of LRU loop
        //  - Poll backend always, frontend only if 1+ worker ready
        //  - If worker replies, queue worker as ready and forward reply
        //    to client if necessary
        //  - If client requests, pop next worker and send request to it
        //
        //  A very simple queue structure with known max size
        Queue<ZFrame> worker_queue = new LinkedList<ZFrame>();

        while (!Thread.currentThread().isInterrupted()) {

            //  Initialize poll set
            Poller items = context.getContext().poller(2);

            //  Always poll for worker activity on backend
            items.register(backend, Poller.POLLIN);

            //  Poll front-end only if we have available workers
            if(worker_queue.size()>0)
                items.register(frontend, Poller.POLLIN);

            if(items.poll() < 0)
                break;

            //  Handle worker activity on backend
            if (items.pollin(0)) {

                ZMsg msg = ZMsg.recvMsg(backend);
                if (msg == null)
                    break;
                ZFrame address = msg.unwrap();
                //  Queue worker address for LRU routing
                worker_queue.add(address);

                //  Forward message to client if it's not a READY
                ZFrame frame = msg.getFirst();
                if (new String(frame.getData()).equals(LRU_READY))
                    msg.destroy();
                else
                    msg.send(frontend);

            }

            if (items.pollin(1)) {
                //  Now get next client request, route to LRU worker
                //  Client request is [address][empty][request]
                
                ZMsg msg = ZMsg.recvMsg(frontend);
                if (msg != null) {
                    msg.wrap(worker_queue.poll());
                    msg.send(backend);
                }

            }

        }
        
        for (ZFrame frame: worker_queue) {
            frame.destroy();
        }

        context.destroy();

        System.exit(0);

    }

}
