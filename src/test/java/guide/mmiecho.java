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

import org.zeromq.ZMsg;

/**
 * MMI echo query example
 */
public class mmiecho {

    public static void main(String[] args) {
        boolean verbose = (args.length > 0 && "-v".equals(args[0]));
        mdcliapi clientSession = new mdcliapi("tcp://localhost:5555", verbose);

        ZMsg request = new ZMsg();

        // This is the service we want to look up
        request.addString("echo");

        // This is the service we send our request to
        ZMsg reply = clientSession.send("mmi.service", request);

        if (reply != null) {
            String replyCode = reply.getFirst().toString();
            System.out.printf("Lookup echo service: %s\n", replyCode);
        } else {
            System.out
                    .println("E: no response from broker, make sure it's running");
        }

        clientSession.destroy();
    }

}
