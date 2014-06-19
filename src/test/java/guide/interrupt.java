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

/*
*
*  Interrupt in Java
*  Shows how to handle Ctrl-C
*/

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

public class interrupt {
   public static void main (String[] args) {
      //  Prepare our context and socket
      final ZMQ.Context context = ZMQ.context(1);

      final Thread zmqThread = new Thread() {
         @Override
         public void run() {
            ZMQ.Socket socket = context.socket(ZMQ.REP);
            socket.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.recv (0);
                } catch (ZMQException e) {
                    if (e.getErrorCode () == ZMQ.Error.ETERM.getCode ()) {
                        break;
                    }
                }
            }

            socket.close();
         }
      };

      Runtime.getRuntime().addShutdownHook(new Thread() {
         @Override
         public void run() {
            System.out.println("W: interrupt received, killing server...");
            context.term();
            try {
               zmqThread.interrupt();
               zmqThread.join();
            } catch (InterruptedException e) {
            }
         }
      });

      zmqThread.start();
   }
}
