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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * Clone client Model Two
 * 
 * @author Danish Shrestha <dshrestha06@gmail.com>
 * 
 */
public class clonecli2 {
	private static Map<String, kvsimple> kvMap = new HashMap<String, kvsimple>();

	public void run() {
		Context ctx = ZMQ.context(1);
		Socket snapshot = ctx.socket(ZMQ.DEALER);
		snapshot.connect("tcp://localhost:5556");

		Socket subscriber = ctx.socket(ZMQ.SUB);
		subscriber.connect("tcp://localhost:5557");
		subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

		// get state snapshot
		snapshot.send("ICANHAZ?".getBytes(ZMQ.CHARSET), 0);
        long sequence = 0;
		while (true) {
            kvsimple kvMsg = kvsimple.recv(snapshot);
            if (kvMsg == null)
                break;
			sequence = kvMsg.getSequence();
			if ("KTHXBAI".equalsIgnoreCase(kvMsg.getKey())) {
				System.out.println("Received snapshot = " + kvMsg.getSequence());
				break; // done
			}

			System.out.println("receiving " + kvMsg.getSequence());
			clonecli2.kvMap.put(kvMsg.getKey(), kvMsg);
		}

		// now apply pending updates, discard out-of-getSequence messages
		while (true) {
            kvsimple kvMsg = kvsimple.recv(subscriber);

            if (kvMsg == null)
                break;

            if (kvMsg.getSequence() > sequence) {
                sequence = kvMsg.getSequence();
                System.out.println("receiving " + sequence);
                clonecli2.kvMap.put(kvMsg.getKey(), kvMsg);
            }
		}
	}

	public static void main(String[] args) {
		new clonecli2().run();
	}
}
