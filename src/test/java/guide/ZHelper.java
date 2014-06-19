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
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ZHelper
{
    private static Random rand = new Random(System.currentTimeMillis ());

    /**
     * Receives all message parts from socket, prints neatly
     */
    public static void dump (Socket sock)
    {
        System.out.println("----------------------------------------");
        while(true) {
            byte [] msg = sock.recv (0);
            boolean isText = true;
            String data = "";
            for (int i = 0; i< msg.length; i++) {
                if (msg[i] < 32 || msg[i] > 127)
                    isText = false;
                data += String.format ("%02X", msg[i]);
            }
            if (isText)
                data = new String (msg, ZMQ.CHARSET);

            System.out.println (String.format ("[%03d] %s", msg.length, data));
            if (!sock.hasReceiveMore ())
                break;
        }
    }

    public static void setId (Socket sock)
    {
        String identity = String.format ("%04X-%04X", rand.nextInt (), rand.nextInt ());

        sock.setIdentity (identity.getBytes (ZMQ.CHARSET));
    }

    public static List<Socket> buildZPipe(Context ctx) {
        Socket socket1 = ctx.socket(ZMQ.PAIR);
        socket1.setLinger(0);
        socket1.setHWM(1);

        Socket socket2 = ctx.socket(ZMQ.PAIR);
        socket2.setLinger(0);
        socket2.setHWM(1);

        String iface = "inproc://" + new BigInteger(130, rand).toString(32);
        socket1.bind(iface);
        socket2.connect(iface);

        return Arrays.asList(socket1, socket2);
    }
}
