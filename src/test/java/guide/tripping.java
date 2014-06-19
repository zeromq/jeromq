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

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

/**
* Round-trip demonstrator. Broker, Worker and Client are mocked as separate
* threads.
*/
public class tripping {

    static class Broker implements Runnable {
        @Override
        public void run() {
            ZContext ctx = new ZContext();
            Socket frontend = ctx.createSocket(ZMQ.ROUTER);
            Socket backend = ctx.createSocket(ZMQ.ROUTER);
            frontend.setHWM (0);
            backend.setHWM (0);
            frontend.bind("tcp://*:5555");
            backend.bind("tcp://*:5556");

            while (!Thread.currentThread().isInterrupted()) {
                ZMQ.Poller items = new ZMQ.Poller(2);
                items.register(frontend, ZMQ.Poller.POLLIN);
                items.register(backend, ZMQ.Poller.POLLIN);
                
                if (items.poll() == -1)
                    break; // Interrupted
                if (items.pollin(0)) {
                    ZMsg msg = ZMsg.recvMsg(frontend);
                    if (msg == null)
                        break; // Interrupted
                    ZFrame address = msg.pop();
                    address.destroy();
                    msg.addFirst(new ZFrame("W"));
                    msg.send(backend);
                }
                if (items.pollin(1)) {

                    ZMsg msg = ZMsg.recvMsg(backend);
                    if (msg == null)
                        break; // Interrupted
                    ZFrame address = msg.pop();
                    address.destroy();
                    msg.addFirst(new ZFrame("C"));
                    msg.send(frontend);
                }
            }
            ctx.destroy();
        }

    }

    static class Worker implements Runnable {

        @Override
        public void run() {
            ZContext ctx = new ZContext();
            Socket worker = ctx.createSocket(ZMQ.DEALER);
            worker.setHWM (0);
            worker.setIdentity("W".getBytes(ZMQ.CHARSET));
            worker.connect("tcp://localhost:5556");
            while (!Thread.currentThread().isInterrupted()) {
                ZMsg msg = ZMsg.recvMsg(worker);
                msg.send(worker);
            }

            ctx.destroy();

        }

    }

    static class Client implements Runnable {
        private static int SAMPLE_SIZE = 10000;

        @Override
        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.DEALER);
            client.setHWM (0);
            client.setIdentity("C".getBytes(ZMQ.CHARSET));
            client.connect("tcp://localhost:5555");
            System.out.println("Setting up test");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int requests;
            long start;

            System.out.println("Synchronous round-trip test");
            start = System.currentTimeMillis();

            for (requests = 0; requests < SAMPLE_SIZE; requests++) {
                ZMsg req = new ZMsg();
                req.addString("hello");
                req.send(client);
                ZMsg.recvMsg(client).destroy();
            }

            System.out.printf(" %d calls/second\n",
                    (1000 * SAMPLE_SIZE) / (System.currentTimeMillis() - start));

            System.out.println("Asynchronous round-trip test");
            start = System.currentTimeMillis();

            for (requests = 0; requests < SAMPLE_SIZE; requests++) {
                ZMsg req = new ZMsg();
                req.addString("hello");
                req.send(client);
            }
            for (requests = 0; requests < SAMPLE_SIZE
                    && !Thread.currentThread().isInterrupted(); requests++) {
                ZMsg.recvMsg(client).destroy();
            }

            System.out.printf(" %d calls/second\n",
                    (1000 * SAMPLE_SIZE) / (System.currentTimeMillis() - start));

            ctx.destroy();
        }

    }

    public static void main(String[] args) {
        
        if (args.length==1)
            Client.SAMPLE_SIZE = Integer.parseInt(args[0]);

        Thread brokerThread = new Thread(new Broker());
        Thread workerThread = new Thread(new Worker());
        Thread clientThread = new Thread(new Client());

        brokerThread.setDaemon(true);
        workerThread.setDaemon(true);

        brokerThread.start();
        workerThread.start();
        clientThread.start();

        try {
            clientThread.join();
            workerThread.interrupt();
            brokerThread.interrupt();
            Thread.sleep(200);// give them some time
        } catch (InterruptedException e) {
        }
    }

}
