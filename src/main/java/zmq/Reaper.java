/*  
    Copyright (c) 2011 250bpm s.r.o.
    Copyright (c) 2011 Other contributors as noted in the AUTHORS file
          
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
package zmq;

import java.nio.channels.SelectableChannel;

public class Reaper extends ZObject implements IPollEvents {

    //  Reaper thread accesses incoming commands via this mailbox.
    private final Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    private SelectableChannel mailbox_handle;

    //  I/O multiplexing is performed using a poller object.
    private final Poller poller;

    //  Number of sockets being reaped at the moment.
    private int sockets;

    //  If true, we were already asked to terminate.
    private volatile boolean terminating;
    
    private String name;
    
    public Reaper (Ctx ctx_, int tid_) 
    {
        super(ctx_, tid_);
        sockets = 0;
        terminating = false;
        name = "reaper-" + tid_;
        poller = new Poller(name);

        mailbox = new Mailbox(name);
        
        mailbox_handle = mailbox.get_fd();
        poller.add_fd (mailbox_handle, this);
        poller.set_pollin (mailbox_handle);
    }

    public void destroy () {
        poller.destroy();
        mailbox.close();
    }
    
    public Mailbox get_mailbox() {
        return mailbox;
    }
    
    public void start() {
        poller.start();
        
    }

    public void stop() {
        if (!terminating)
            send_stop ();
    }



    @Override
    public void in_event() {

        while (true) {

            //  Get the next command. If there is none, exit.
            Command cmd = mailbox.recv (0);
            if (cmd == null)
                break;
                
            //  Process the command.
            cmd.destination().process_command (cmd);
        }

    }
    

    @Override
    public void out_event() {
        throw new UnsupportedOperationException();    
    }
    
    @Override
    public void connect_event() {
        throw new UnsupportedOperationException();    
    }
    
    @Override
    public void accept_event() {
        throw new UnsupportedOperationException();    
    }

    @Override
    public void timer_event(int id_) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void process_stop ()
    {
        terminating = true;

        //  If there are no sockets being reaped finish immediately.
        if (sockets == 0) {
            send_done ();
            poller.rm_fd (mailbox_handle);
            poller.stop ();
        }
    }
    
    @Override
    protected void process_reap (SocketBase socket_)
    {   
        //  Add the socket to the poller.
        socket_.start_reaping (poller);

        ++sockets;
    }



    @Override
    protected void process_reaped ()
    {
        --sockets;

        //  If reaped was already asked to terminate and there are no more sockets,
        //  finish immediately.
        if (sockets == 0 && terminating) {
            send_done ();
            poller.rm_fd (mailbox_handle);
            poller.stop ();
            
        }
    }


}
