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
 * Multithreaded relay
 */
public class mtrelay{

    private static class Step1 extends Thread
    {
        private Context context;

        private Step1 (Context context)
        {
            this.context = context;
        }

        @Override
        public void run(){
            //  Signal downstream to step 2
            Socket xmitter = context.socket(ZMQ.PAIR);
            xmitter.connect("inproc://step2");
            System.out.println ("Step 1 ready, signaling step 2");
            xmitter.send("READY", 0);
            xmitter.close ();
        }

    }
    private static class Step2 extends Thread
    {
        private Context context;

        private Step2 (Context context)
        {
            this.context = context;
        }

        @Override
        public void run(){
            //  Bind to inproc: endpoint, then start upstream thread
            Socket receiver = context.socket(ZMQ.PAIR);
            receiver.bind("inproc://step2");
            Thread step1 = new Step1 (context);
            step1.start();

            //  Wait for signal
            receiver.recv(0);
            receiver.close ();

            //  Connect to step3 and tell it we're ready
            Socket xmitter = context.socket(ZMQ.PAIR);
            xmitter.connect("inproc://step3");
            xmitter.send("READY", 0);

            xmitter.close ();
        }

    }
    public static void main (String[] args) {

        Context context = ZMQ.context(1);
    
        //  Bind to inproc: endpoint, then start upstream thread
        Socket receiver = context.socket(ZMQ.PAIR);
        receiver.bind("inproc://step3");
        
        //  Step 2 relays the signal to step 3
        Thread step2 = new Step2 (context);
        step2.start();
        
        //  Wait for signal
        receiver.recv(0);
        receiver.close ();
    
        System.out.println ("Test successful!");
        context.term ();
    }
}
