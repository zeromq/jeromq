/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package guide;

import org.zeromq.ZMQ;

//
//Custom routing Router to Mama (ROUTER to REQ)
//
public class rtmama {

    private static final int NBR_WORKERS  =10;
    
    public static class Worker implements Runnable {
        private final byte[] END = "END".getBytes(ZMQ.CHARSET);

        public void run() {
            ZMQ.Context context = ZMQ.context(1);
            ZMQ.Socket worker = context.socket(ZMQ.REQ);
            // worker.setIdentity(); will set a random id automatically
            worker.connect("ipc://routing.ipc");

            int total = 0;
            while (true) {
                worker.send("ready", 0);
                byte[] workerload = worker.recv(0);
                if (new String(workerload, ZMQ.CHARSET).equals("END")) {
                    System.out.println(
                        String.format(
                            "Processs %d tasks.", total
                        )
                    );
                    break;
                }
                total += 1;
            }
            worker.close();
            context.term();
        }
    }
    public static void main(String[] args) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket client = context.socket(ZMQ.ROUTER);
        client.bind("ipc://routing.ipc");

        for (int i = 0 ; i != NBR_WORKERS; i++) {
            new Thread(new Worker()).start();
        }
        

        for (int i = 0 ; i != NBR_WORKERS; i++) {
            //  LRU worker is next waiting in queue
            byte[] address = client.recv(0);
            byte[] empty = client.recv(0);
            byte[] ready = client.recv(0);
            
            client.send(address, ZMQ.SNDMORE);
            client.send("", ZMQ.SNDMORE);
            client.send("This is the workload", 0);
        }
        
        for (int i = 0 ; i != NBR_WORKERS; i++) {
            //  LRU worker is next waiting in queue
            byte[] address = client.recv(0);
            byte[] empty = client.recv(0);
            byte[] ready = client.recv(0);
            
            client.send(address, ZMQ.SNDMORE);
            client.send("", ZMQ.SNDMORE);
            client.send("END", 0);
        }
        
        //  Now ask mamas to shut down and report their results
        client.close();
        context.term();
    }

}
