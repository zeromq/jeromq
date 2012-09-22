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


//  Simple base class for objects that live in I/O threads.
//  It makes communication with the poller object easier and
//  makes defining unneeded event handlers unnecessary.

public class IOObject implements IPollEvents {

    private Poller poller;
    private IPollEvents handler;
    
    public IOObject(IOThread io_thread_) {
        if (io_thread_ != null) {
            plug(io_thread_);
        }
    }

    //  When migrating an object from one I/O thread to another, first
    //  unplug it, then migrate it, then plug it to the new thread.
    
    public void plug(IOThread io_thread_) {
        assert (io_thread_ != null);
        assert (poller == null);

        //  Retrieve the poller from the thread we are running in.
        poller = io_thread_.get_poller ();    
    }
    
    public void unplug() {
        assert (poller != null);

        //  Forget about old poller in preparation to be migrated
        //  to a different I/O thread.
        poller = null;
        handler = null;
    }

    public final void add_fd (SelectableChannel fd_)
    {
        poller.add_fd (fd_, this);
    }
    
    public final void rm_fd(SelectableChannel handle) {
        poller.rm_fd(handle);
    }
    
    public final void set_pollin (SelectableChannel handle_)
    {
        poller.set_pollin (handle_);
    }

    public final void set_pollout (SelectableChannel handle_)
    {
        poller.set_pollout (handle_);
    }

    public final void set_pollconnect(SelectableChannel handle) {
        poller.set_pollconnect(handle);
    }
    
    public final void set_pollaccept(SelectableChannel handle) {
        poller.set_pollaccept(handle);
    }
    
    public final void reset_pollin(SelectableChannel handle) {
        poller.reset_pollin (handle);
    }


    public final void reset_pollout(SelectableChannel handle) {
        poller.reset_pollout (handle);
    }

    
    @Override
    public final void in_event() {
        handler.in_event();
    }

    @Override
    public final void out_event() {
        handler.out_event();
    }
    
    @Override
    public final void connect_event() {
        handler.connect_event();
    }

    @Override
    public final void accept_event() {
        handler.accept_event();
    }
    
    @Override
    public final void timer_event(int id_) {
        handler.timer_event(id_);
    }
    
    public final void add_timer (long timeout_, int id_)
    {
        poller.add_timer (timeout_, this, id_);
    }

    public final void set_handler(IPollEvents handler) {
        this.handler = handler;
    }




    public void cancel_timer(int id_) {
        poller.cancel_timer(this, id_);
    }



}
