package guide;

import java.util.ArrayList;
import java.util.Random;

import org.jeromq.ZContext;
import org.jeromq.ZFrame;
import org.jeromq.ZMQ;
import org.jeromq.ZMQ.PollItem;
import org.jeromq.ZMQ.Socket;
import org.jeromq.ZMsg;

//
//Broker peering simulation (part 2)
//Prototypes the request-reply flow
//

public class peering3 {

    private static final int NBR_CLIENTS = 10;
    private static final int NBR_WORKERS = 5;
    private static final String LRU_READY =  "\001";      //  Signals worker is ready
    
    //  Our own name; in practice this would be configured per node
    private static String self;
    
    //  This is the client task. It issues a burst of requests and then
    //  sleeps for a few seconds. This simulates sporadic activity; when
    //  a number of clients are active at once, the local workers should
    //  be overloaded. The client uses a REQ socket for requests and also
    //  pushes statistics to the monitor socket:
    private static class client_task extends Thread {
        @Override
        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.REQ);
            client.connect(String.format("ipc://%s-localfe.ipc", self));
            Socket monitor = ctx.createSocket(ZMQ.PUSH);
            monitor.connect(String.format("ipc://%s-monitor.ipc", self));
            Random rand = new Random(System.nanoTime());
            
            while (true) {
                
                try {
                    Thread.sleep(rand.nextInt(5) * 1000);
                } catch (InterruptedException e1) {
                }
                int burst = rand.nextInt(15);
                
                while (burst > 0) {
                    System.out.println(Thread.currentThread().getName() + " " + burst);
                    String task_id = String.format("%04X", rand.nextInt(10000));
                    //  Send request, get reply
                    client.send(task_id ,0);
                    
                    //  Wait max ten seconds for a reply, then complain
                    PollItem pollset [] = { new PollItem(client, ZMQ.POLLIN) };
                    int rc = ZMQ.poll (pollset, 10 * 1000 );
                    if (rc == -1)
                        break;          //  Interrupted

                    if (pollset [0].isReadable()) {
                        String reply = client.recvStr(0); 
                        if (reply == null)
                            break;              //  Interrupted
                        //  Worker is supposed to answer us with our task id
                        assert (reply.equals(task_id));
                        monitor.send(String.format("%s", reply),0);
                    }
                    else {
                        monitor.send(
                            String.format("E: CLIENT EXIT - lost task %s", task_id),0);
                        ctx.destroy();
                        return;
                    }
                    burst--;
                }
            }
        }
    }
    
    //  This is the worker task, which uses a REQ socket to plug into the LRU
    //  router. It's the same stub worker task you've seen in other examples:
    
    private static class worker_task extends Thread {
        @Override
        public void run() {
            Random rand = new Random(System.nanoTime());
            ZContext ctx = new ZContext();
            Socket worker = ctx.createSocket(ZMQ.REQ);
            worker.connect(String.format("ipc://%s-localbe.ipc", self));

            //  Tell broker we're ready for work
            ZFrame frame = new ZFrame (LRU_READY);
            frame.send(worker, 0);
            
            while (true) {
                //  Send request, get reply
                ZMsg msg = ZMsg.recvMsg(worker, 0);
                if (msg == null)
                    break;              //  Interrupted
                
                //  Workers are busy for 0/1 seconds
                try {
                    Thread.sleep (rand.nextInt (2) * 1000);
                } catch (InterruptedException e) {
                }
                
                msg.send(worker);
                
            }
            ctx.destroy();
        }
    }
    
    //  The main task begins by setting-up all its sockets. The local frontend
    //  talks to clients, and our local backend talks to workers. The cloud
    //  frontend talks to peer brokers as if they were clients, and the cloud
    //  backend talks to peer brokers as if they were workers. The state
    //  backend publishes regular state messages, and the state frontend
    //  subscribes to all state backends to collect these messages. Finally,
    //  we use a PULL monitor socket to collect printable messages from tasks:
    public static void main (String[] argv)
    {
        //  First argument is this broker's name
        //  Other arguments are our peers' names
        //
        if (argv.length < 1) {
            System.out.println ("syntax: peering3 me {you}\n");
            System.exit(-1);
        }
        self = argv [0];
        System.out.println (String.format("I: preparing broker at %s\n", self));
        Random rand = new Random(System.nanoTime());
    
        ZContext ctx = new ZContext();
        
        //  Prepare local frontend and backend
        Socket localfe = ctx.createSocket(ZMQ.ROUTER);
        localfe.bind( String.format("ipc://%s-localfe.ipc", self));
        Socket localbe = ctx.createSocket(ZMQ.ROUTER);
        localbe.bind( String.format("ipc://%s-localbe.ipc", self));
        

        //  Bind cloud frontend to endpoint
        Socket cloudfe = ctx.createSocket(ZMQ.ROUTER);
        cloudfe.setIdentity(self);
        cloudfe.bind( String.format("ipc://%s-cloud.ipc", self));
        
        //  Connect cloud backend to all peers
        Socket cloudbe = ctx.createSocket(ZMQ.ROUTER);
        cloudbe.setIdentity(self);
        int argn;
        for (argn = 1; argn < argv.length; argn++) {
            String peer = argv [argn];
            System.out.println(String.format("I: connecting to cloud forintend at '%s'\n", peer));
            cloudbe.connect( String.format("ipc://%s-cloud.ipc", peer) );
        }
        
        //  Bind state backend to endpoint
        Socket statebe = ctx.createSocket(ZMQ.PUB);
        statebe.bind( String.format("ipc://%s-state.ipc", self));
        
        //  Connect statefe to all peers
        Socket statefe = ctx.createSocket(ZMQ.SUB);
        statefe.subscribe("");
        for (argn = 1; argn < argv.length; argn++) {
            String peer = argv [argn];
            System.out.println(String.format("I: connecting to state backend at '%s'\n", peer));
            statefe.connect( String.format("ipc://%s-state.ipc", peer) );
        }

        //  Prepare monitor socket
        Socket monitor = ctx.createSocket(ZMQ.PULL);
        monitor.bind( String.format("ipc://%s-monitor.ipc", self));
        
        //  Start local workers
        int worker_nbr;
        for (worker_nbr = 0; worker_nbr < NBR_WORKERS; worker_nbr++)
            new worker_task().start();

        //  Start local clients
        int client_nbr;
        for (client_nbr = 0; client_nbr < NBR_CLIENTS; client_nbr++)
            new client_task().start();

        //  Queue of available workers
        int local_capacity = 0;
        int cloud_capacity = 0;
        ArrayList <ZFrame> workers = new ArrayList<ZFrame>();

        //  The main loop has two parts. First we poll workers and our two service
        //  sockets (statefe and monitor), in any case. If we have no ready workers,
        //  there's no point in looking at incoming requests. These can remain on
        //  their internal 0MQ queues:
        
        while (true) {
            //  First, route any waiting replies from workers
            PollItem primary [] = {
                new PollItem( localbe, ZMQ.POLLIN ),
                new PollItem( cloudbe, ZMQ.POLLIN ),
                new PollItem( statefe, ZMQ.POLLIN ),
                new PollItem( monitor, ZMQ.POLLIN )
            };
            //  If we have no workers anyhow, wait indefinitely
            int rc = ZMQ.poll (primary,
                local_capacity > 0? 1000 : -1);
            if (rc == -1)
                break;              //  Interrupted
            
            //  Track if capacity changes during this iteration
            int previous = local_capacity;


            //  Handle reply from local worker
            ZMsg msg = null;
            if (primary [0].isReadable()) {
                msg = ZMsg.recvMsg(localbe);
                if (msg == null)
                    break;          //  Interrupted
                ZFrame address = msg.unwrap();
                workers.add( address);
                local_capacity++;

                //  If it's READY, don't route the message any further
                ZFrame frame = msg.getFirst();
                if (new String(frame.getData()).equals(LRU_READY)) {
                    msg.destroy();
                    msg = null;
                }
            }
            //  Or handle reply from peer broker
            else
            if (primary [1].isReadable()) {
                msg = ZMsg.recvMsg (cloudbe);
                if (msg == null)
                    break;          //  Interrupted
                //  We don't use peer broker address for anything
                ZFrame address = msg.unwrap();
                address.destroy();
                System.out.println("Recv from cloudbe");
            }
            //  Route reply to cloud if it's addressed to a broker
            for (argn = 1; msg != null && argn < argv.length; argn++) {
                byte [] data = msg.getFirst().data();
                if (argv [argn].equals(new String(data))) {
                    msg.send(cloudfe);
                    msg = null;
                }
            }
            //  Route reply to client if we still need to
            if (msg != null)
                msg.send(localfe);
            
            //  If we have input messages on our statefe or monitor sockets we
            //  can process these immediately:

            if (primary [2].isReadable()) {
                String peer = statefe.recvStr();
                String status = statefe.recvStr();
                cloud_capacity = Integer.parseInt (status);
            }
            if (primary [3].isReadable()) {
                String status = monitor.recvStr();
                System.out.println (String.format("%s\n", status));
            }

            //  Now we route as many client requests as we have worker capacity
            //  for. We may reroute requests from our local frontend, but not from //
            //  the cloud frontend. We reroute randomly now, just to test things
            //  out. In the next version we'll do this properly by calculating
            //  cloud capacity://

            while (local_capacity + cloud_capacity > 0) {
                PollItem secondary [] = {
                    new PollItem( localfe, ZMQ.POLLIN ),
                    new PollItem( cloudfe, ZMQ.POLLIN )
                };
                
                if (local_capacity == 0)
                    secondary[1].interestOps(0);
                
                rc = ZMQ.poll (secondary, 0);
                assert (rc >= 0);
                
                if (secondary [0].isReadable()) {
                    msg = ZMsg.recvMsg (localfe);
                }
                else if (secondary [1].isReadable()) {
                    msg = ZMsg.recvMsg (cloudfe);
                }
                else
                    break;      //  No work, go back to backends

                if (local_capacity > 0) {
                    ZFrame frame = workers.remove(0);
                    msg.wrap(frame);
                    msg.send(localbe);
                    local_capacity--;

                } else {
                    //  Route to random broker peer
                    int random_peer = rand.nextInt (argv.length - 1) + 1;
                    msg.push(argv [random_peer]);
                    msg.send(cloudbe);
                    System.out.println("Sent to cloudbe " + argv [random_peer]);
                }
            }
            
            //  We broadcast capacity messages to other peers; to reduce chatter
            //  we do this only if our capacity changed.

            if (local_capacity != previous) {
                //  We stick our own address onto the envelope
                statebe.sendMore(self);
                //  Broadcast new capacity
                statebe.send(String.format("%d", local_capacity), 0);
                System.out.println("Sent to statebe " + local_capacity);
            }
        }
        //  When we're done, clean up properly
        while (workers.size()>0) {
            ZFrame frame = workers.remove(0);
            frame.destroy();
        }
        
        ctx.destroy();
    }
}
