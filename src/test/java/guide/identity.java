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

/**
 * Demonstrate identities as used by the request-reply pattern.
 */
public class identity {

    public static void main (String[] args) throws InterruptedException {

        Context context = ZMQ.context(1);
        Socket sink = context.socket(ZMQ.ROUTER);
        sink.bind("inproc://example");

        //  First allow 0MQ to set the identity, [00] + random 4byte
        Socket anonymous = context.socket(ZMQ.REQ);

        anonymous.connect("inproc://example");
        anonymous.send ("ROUTER uses a generated UUID",0);
        ZHelper.dump (sink);

        //  Then set the identity ourself
        Socket identified = context.socket(ZMQ.REQ);
        identified.setIdentity("Hello".getBytes (ZMQ.CHARSET));
        identified.connect ("inproc://example");
        identified.send("ROUTER socket uses REQ's socket identity", 0);
        ZHelper.dump (sink);

        sink.close ();
        anonymous.close ();
        identified.close();
        context.term();

    }
}
