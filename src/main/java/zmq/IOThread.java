/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

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


public class IOThread extends ZObject implements IPollEvents {

    //  I/O thread accesses incoming commands via this mailbox.
    final private Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    final private SelectableChannel mailbox_handle;

    //  I/O multiplexing is performed using a poller object.
    final private Poller poller;
    
    final String name;
    
    public IOThread(Ctx ctx_, int tid_) {
        super(ctx_, tid_);
        name = "iothread-" + tid_;
        poller = new Poller(name);

        mailbox = new Mailbox(name);
        mailbox_handle = mailbox.get_fd();
        poller.add_fd (mailbox_handle, this);
        poller.set_pollin (mailbox_handle);
        
    }
    
    public void start() {
        poller.start();
    }
    
    public void destroy() {
        poller.destroy();
        mailbox.close();
    }
    public void stop ()
    {
        send_stop ();
    }

    public Mailbox get_mailbox() {
        return mailbox;
    }

    
    public int get_load ()
    {
        return poller.get_load ();
    }


    @Override
    public void in_event() {
        //  TODO: Do we want to limit number of commands I/O thread can
        //  process in a single go?

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


    public Poller get_poller() {
        
        assert (poller != null);
        return poller;
    }
    
    protected void process_stop ()
    {
        poller.rm_fd (mailbox_handle);
        
        poller.stop ();

    }


}
