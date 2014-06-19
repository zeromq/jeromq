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
//  Task sink in Java
//  Binds PULL socket to tcp://localhost:5558
//  Collects results from workers via that socket
//
public class tasksink {

    public static void main (String[] args) throws Exception {

        //  Prepare our context and socket
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket receiver = context.socket(ZMQ.PULL);
        receiver.bind("tcp://*:5558");

        //  Wait for start of batch
        String string = new String(receiver.recv(0), ZMQ.CHARSET);

        //  Start our clock now
        long tstart = System.currentTimeMillis();

        //  Process 100 confirmations
        int task_nbr;
        int total_msec = 0;     //  Total calculated cost in msecs
        for (task_nbr = 0; task_nbr < 100; task_nbr++) {
            string = new String(receiver.recv(0), ZMQ.CHARSET).trim();
            if ((task_nbr / 10) * 10 == task_nbr) {
                System.out.print(":");
            } else {
                System.out.print(".");
            }
        }
        //  Calculate and report duration of batch
        long tend = System.currentTimeMillis();

        System.out.println("\nTotal elapsed time: " + (tend - tstart) + " msec");
        receiver.close();
        context.term();
    }
}
