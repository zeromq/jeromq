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

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * Clone client model 1
 * @author Danish Shrestha <dshrestha06@gmail.com>
 *
 */
public class clonecli1 {
	private static Map<String, kvsimple> kvMap = new HashMap<String, kvsimple>();
	private static AtomicLong sequence = new AtomicLong();

	public void run() {
		ZContext ctx = new ZContext();
		Socket subscriber = ctx.createSocket(ZMQ.SUB);
		subscriber.connect("tcp://localhost:5556");
		subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

		while (true) {
			kvsimple kvMsg = kvsimple.recv(subscriber);
            if (kvMsg == null)
                break;

            clonecli1.kvMap.put(kvMsg.getKey(), kvMsg);
            System.out.println("receiving " + kvMsg);
            sequence.incrementAndGet();
		}
        ctx.destroy();
	}

	public static void main(String[] args) {
		new clonecli1().run();
	}
}
