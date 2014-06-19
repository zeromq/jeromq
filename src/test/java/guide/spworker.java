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

import java.util.Random;

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

//
// Simple Pirate worker
// Connects REQ socket to tcp://*:5556
// Implements worker part of load-balancing queueing
//
public class spworker
{

    private final static String WORKER_READY = "\001";      //  Signals worker is ready

    public static void main(String[] args) throws Exception
    {
        ZContext ctx = new ZContext();
        Socket worker = ctx.createSocket(ZMQ.REQ);

        //  Set random identity to make tracing easier
        Random rand = new Random(System.nanoTime());
        String identity = String.format("%04X-%04X", rand.nextInt(0x10000), rand.nextInt(0x10000));
        worker.setIdentity(identity.getBytes(ZMQ.CHARSET));
        worker.connect("tcp://localhost:5556");

        //  Tell broker we're ready for work
        System.out.printf("I: (%s) worker ready\n", identity);
        ZFrame frame = new ZFrame(WORKER_READY);
        frame.send(worker, 0);

        int cycles = 0;
        while (true) {
            ZMsg msg = ZMsg.recvMsg(worker);
            if (msg == null)
                break;              //  Interrupted

            //  Simulate various problems, after a few cycles
            cycles++;
            if (cycles > 3 && rand.nextInt(5) == 0) {
                System.out.printf("I: (%s) simulating a crash\n", identity);
                msg.destroy();
                break;
            } else if (cycles > 3 && rand.nextInt(5) == 0) {
                System.out.printf("I: (%s) simulating CPU overload\n", identity);
                Thread.sleep(3000);
            }
            System.out.printf("I: (%s) normal reply\n", identity);
            Thread.sleep(1000); //  Do some heavy work
            msg.send(worker);
        }
        ctx.destroy();
    }

}
