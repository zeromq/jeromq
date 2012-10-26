/*
    Copyright other contributors as noted in the AUTHORS file.
                
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
package org.jeromq;

import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Socket;
import org.jeromq.ZMQ.PollItem;
import org.junit.Test;


public class TestZLoop {

    @Test
    public void testZLoop() {
        printf (" * zloop: ");
        int rc = 0;
        //  @selftest
        ZContext ctx = new ZContext ();
        assert (ctx != null);
        
        Socket output = ctx.createSocket(ZMQ.PAIR);
        assert (output != null);
        output.bind("inproc://zloop.test");
        Socket input = ctx.createSocket(ZMQ.PAIR);
        assert (input != null);
        input.connect( "inproc://zloop.test");

        ZLoop loop = ZLoop.instance();
        assert (loop != null);
        loop.verbose (true);
        
        ZLoop.IZLoopHandler s_timer_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                ((Socket)arg).send("PING", 0);
                return 0;
            }
        };
        
        ZLoop.IZLoopHandler s_socket_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                //  Just end the reactor
                return -1;
            }
        };

        //  After 10 msecs, send a ping message to output
        loop.timer ( 10, 1, s_timer_event, output);
        
        //  When we get the ping message, end the reactor
        PollItem poll_input = new PollItem( input, ZMQ.POLLIN );
        rc = loop.poller (poll_input, s_socket_event, null);
        assert (rc == 0);
        loop.start ();

        loop.pollerEnd(poll_input);
        ctx.destroy();
        //  @end
        printf ("OK\n");
    }
    
    @Test
    public void testZLoopAddTimerFromTimer() {
    	printf (" * zloop add timer from another timer: ");
        int rc = 0;
        //  @selftest
        ZContext ctx = new ZContext ();
        assert (ctx != null);
        
        Socket output = ctx.createSocket(ZMQ.PAIR);
        assert (output != null);
        output.bind("inproc://zloop.test");
        Socket input = ctx.createSocket(ZMQ.PAIR);
        assert (input != null);
        input.connect( "inproc://zloop.test");

        ZLoop loop = ZLoop.instance();
        assert (loop != null);
        loop.verbose (true);
        
        ZLoop.IZLoopHandler s_timer_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
            	final long now = System.currentTimeMillis();

            	ZLoop.IZLoopHandler s_timer_event2 = new ZLoop.IZLoopHandler() {	
					@Override
					public int handle(ZLoop loop, PollItem item, Object arg) {
						final long now2 = System.currentTimeMillis();
						assert (now2 >= now + 10);
						((Socket)arg).send("PING", 0);
						return 0;
					}
				};
				loop.timer(10, 1, s_timer_event2, arg);
                return 0;
            }
        };
        
        ZLoop.IZLoopHandler s_socket_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                //  Just end the reactor
                return -1;
            }
        };

        //  After 10 msecs, fire a timer that registers 
        //  another timer that sends the ping message
        loop.timer ( 10, 1, s_timer_event, output);
        
        //  When we get the ping message, end the reactor
        PollItem poll_input = new PollItem( input, ZMQ.POLLIN );
        rc = loop.poller (poll_input, s_socket_event, null);
        assert (rc == 0);
        
        loop.start ();
        
        loop.pollerEnd(poll_input);
        ctx.destroy();
    	//  @end
        printf ("OK\n");
    }
    
    @Test(timeout = 1000)
    public void testZLoopEndReactorFromTimer() {
        printf (" * zloop end reactor from timer: ");
        int rc = 0;
        //  @selftest
        ZContext ctx = new ZContext ();
        assert (ctx != null);
        
        Socket output = ctx.createSocket(ZMQ.PAIR);
        assert (output != null);
        output.bind("inproc://zloop.test");
        Socket input = ctx.createSocket(ZMQ.PAIR);
        assert (input != null);
        input.connect( "inproc://zloop.test");

        ZLoop loop = ZLoop.instance();
        assert (loop != null);
        loop.verbose (true);
        
        ZLoop.IZLoopHandler s_timer_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                ((Socket)arg).send("PING", 0);
                return 0;
            }
        };
        
        ZLoop.IZLoopHandler s_socket_event = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                //  After 10 msecs, fire an event that ends the reactor
            	ZLoop.IZLoopHandler s_shutdown_event = new ZLoop.IZLoopHandler() {
                    @Override
                    public int handle(ZLoop loop, PollItem item, Object arg) {
                    	//  Just end the reactor
                        return -1;
                    }
                };
                loop.timer(10, 1, s_shutdown_event, s_shutdown_event);
                return 0;
            }
        };

        //  Fire event that sends a ping message to output
        loop.timer (0, 1, s_timer_event, output);
        
        //  When we get the ping message, end the reactor
        PollItem poll_input = new PollItem( input, ZMQ.POLLIN );
        rc = loop.poller (poll_input, s_socket_event, null);
        assert (rc == 0);
        loop.start ();

        loop.pollerEnd(poll_input);
        ctx.destroy();
        //  @end
        printf ("OK\n");
    }

    private static void printf(String s) {
        System.out.print(s);
    }
}
