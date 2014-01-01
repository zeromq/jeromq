/*
    Copyright (c) 2010-2011 250bpm s.r.o.
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Mailbox {

    //  The pipe to store actual commands.
    private final YPipe<Command> cpipe;

    //  Signaler to pass signals from writer thread to reader thread.
    private final Signaler signaler;

    //  There's only one thread receiving from the mailbox, but there
    //  is arbitrary number of threads sending. Given that ypipe requires
    //  synchronised access on both of its endpoints, we have to synchronise
    //  the sending side.
    private final Lock sync;

    //  True if the underlying pipe is active, ie. when we are allowed to
    //  read commands from it.
    private boolean active;
    
    // mailbox name, for better debugging
    private final String name;

    public Mailbox(String name_) {
        cpipe = new YPipe<Command>(Config.COMMAND_PIPE_GRANULARITY.getValue());
        sync = new ReentrantLock();
        signaler = new Signaler();
        
        //  Get the pipe into passive state. That way, if the users starts by
        //  polling on the associated file descriptor it will get woken up when
        //  new command is posted.
        
        Command cmd = cpipe.read ();
        assert (cmd == null);
        active = false;
        
        name = name_;
    }
    

    
    public SelectableChannel get_fd ()
    {
        return signaler.get_fd ();
    }
    
    public void send (final Command cmd_)
    {   
        boolean ok = false;
        sync.lock ();
        try {
            cpipe.write (cmd_, false);
            ok = cpipe.flush ();
        } finally {
            sync.unlock ();
        }
        
        if (!ok) {
            signaler.send ();
        }
    }
    
    public Command recv (long timeout_)
    {
        Command cmd_ = null;
        //  Try to get the command straight away.
        if (active) {
            cmd_ = cpipe.read ();
            if (cmd_ != null) {
                
                return cmd_;
            }

            //  If there are no more commands available, switch into passive state.
            active = false;
            signaler.recv ();
        }


        //  Wait for signal from the command sender.
        boolean rc = signaler.wait_event (timeout_);
        if (!rc)
            return null;

        //  We've got the signal. Now we can switch into active state.
        active = true;

        //  Get a command.
        cmd_ = cpipe.read ();
        assert (cmd_ != null);
        
        return cmd_;
    }

    public void close () {

        //  TODO: Retrieve and deallocate commands inside the cpipe.

        // Work around problem that other threads might still be in our
        // send() method, by waiting on the mutex before disappearing.
        sync.lock ();
        sync.unlock ();

        signaler.close();
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + name + "]";
    }
}
