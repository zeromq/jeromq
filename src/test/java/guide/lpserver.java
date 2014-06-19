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

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

//
// Lazy Pirate server
// Binds REQ socket to tcp://*:5555
// Like hwserver except:
//  - echoes request as-is
//  - randomly runs slowly, or exits to simulate a crash.
//
public class lpserver
{

    public static void main(String[] argv) throws Exception
    {
        Random rand = new Random(System.nanoTime());

        Context context = ZMQ.context(1);
        Socket server = context.socket(ZMQ.REP);
        server.bind("tcp://*:5555");

        int cycles = 0;
        while (true) {
            String request = server.recvStr();
            cycles++;

            //  Simulate various problems, after a few cycles
            if (cycles > 3 && rand.nextInt(3) == 0) {
                System.out.println("I: simulating a crash");
                break;
            } else if (cycles > 3 && rand.nextInt(3) == 0) {
                System.out.println("I: simulating CPU overload");
                Thread.sleep(2000);
            }
            System.out.printf("I: normal request (%s)\n", request);
            Thread.sleep(1000);              //  Do some heavy work
            server.send(request);
        }
        server.close();
        context.term();
    }
}
