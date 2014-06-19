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

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;

import java.util.HashMap;
import java.util.Map;

//  Last value cache
//  Uses XPUB subscription messages to re-send data
public class lvcache
{
    public static void main(String[] args)
    {
        ZContext context = new ZContext();
        Socket frontend = context.createSocket(ZMQ.SUB);
        frontend.bind("tcp://*:5557");
        Socket backend = context.createSocket(ZMQ.XPUB);
        backend.bind("tcp://*:5558");

        //  Subscribe to every single topic from publisher
        frontend.subscribe(ZMQ.SUBSCRIPTION_ALL);

        //  Store last instance of each topic in a cache
        Map<String, String> cache = new HashMap<String, String>();

        //  .split main poll loop
        //  We route topic updates from frontend to backend, and
        //  we handle subscriptions by sending whatever we cached,
        //  if anything:
        while (true) {
            PollItem[] items = {
                    new PollItem(frontend, ZMQ.Poller.POLLIN),
                    new PollItem(backend, ZMQ.Poller.POLLIN),
            };
            if (ZMQ.poll(items, 1000) == -1)
                break;              //  Interrupted

            //  Any new topic data we cache and then forward
            if (items[0].isReadable()) {
                String topic = frontend.recvStr();
                String current = frontend.recvStr();

                if (topic == null)
                    break;
                cache.put(topic, current);
                backend.sendMore(topic);
                backend.send(current);
            }
            //  .split handle subscriptions
            //  When we get a new subscription, we pull data from the cache:
            if (items[1].isReadable()) {
                ZFrame frame = ZFrame.recvFrame(backend);
                if (frame == null)
                    break;
                //  Event is one byte 0=unsub or 1=sub, followed by topic
                byte[] event = frame.getData();
                if (event [0] == 1) {
                    String topic = new String(event, 1, event.length -1, ZMQ.CHARSET);
                    System.out.printf ("Sending cached topic %s\n", topic);
                    String previous = cache.get(topic);
                    if (previous != null) {
                        backend.sendMore(topic);
                        backend.send(previous);
                    }
                }
                frame.destroy();
            }
        }
        context.destroy();
    }
}
