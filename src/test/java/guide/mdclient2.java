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
 * Majordomo Protocol client example, asynchronous. Uses the mdcli API to hide
 * all MDP aspects
 */

public class mdclient2 {

    public static void main(String[] args) {
        boolean verbose = (args.length > 0 && "-v".equals(args[0]));
        mdcliapi2 clientSession = new mdcliapi2("tcp://localhost:5555", verbose);

        int count;
        for (count = 0; count < 100000; count++) {
            ZMsg request = new ZMsg();
            request.addString("Hello world");
            clientSession.send("echo", request);
        }
        for (count = 0; count < 100000; count++) {
            ZMsg reply = clientSession.recv();
            if (reply != null)
                reply.destroy();
            else
                break; // Interrupt or failure
        }

        System.out.printf("%d requests/replies processed\n", count);
        clientSession.destroy();
    }

}
