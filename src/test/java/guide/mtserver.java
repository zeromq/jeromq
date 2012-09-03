package guide;

import org.jeromq.ZMQ;
import org.jeromq.ZMQQueue;

public class mtserver {
   public static void main(String[] args) {

      final ZMQ.Context context = ZMQ.context(1);

      ZMQ.Socket clients = context.socket(ZMQ.ROUTER);
      clients.bind ("tcp://*:5555");

      ZMQ.Socket workers = context.socket(ZMQ.DEALER);
      workers.bind ("inproc://workers");

      for(int thread_nbr = 0; thread_nbr < 5; thread_nbr++) {
         Thread worker_routine = new Thread() {

            @Override
            public void run() {
               ZMQ.Socket socket = context.socket(ZMQ.REP);
               socket.connect ("inproc://workers");

               while (true) {

                  //  Wait for next request from client (C string)
                  byte[] request = socket.recv (0);
                  System.out.println ( Thread.currentThread().getName() + " Received request: ["+new String(request)+"]");

                  //  Do some 'work'
                  try {
                     Thread.sleep (1000);
                  } catch(InterruptedException e) {
                     e.printStackTrace();
                  }

                  //  Send reply back to client (C string)
                  byte[] reply = "World ".getBytes();
                  socket.send(reply, 0);
               }
            }
         };
         worker_routine.start();
      }
      //  Connect work threads to client threads via a queue
      ZMQQueue zMQQueue = new ZMQQueue(context,clients, workers);
      zMQQueue.run();

      //  We never get here but clean up anyhow
      clients.close();
      workers.close();
      context.term();
   }
}