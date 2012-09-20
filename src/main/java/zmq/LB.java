/*
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2011 VMware, Inc.
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

import java.util.ArrayList;
import java.util.List;

public class LB {

    //  List of outbound pipes.
    private final List<Pipe> pipes;
    
    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array.
    private int active;

    //  Points to the last pipe that the most recent message was sent to.
    private int current;

    //  True if last we are in the middle of a multipart message.
    private boolean more;

    //  True if we are dropping current message.
    private boolean dropping;
    
    //  List operations
    private int start;
    
    public LB() {
        active = 0;
        current = 0;
        more = false;
        dropping = false;
        
        start = 0;
        pipes = new ArrayList<Pipe>();
    }

    public void attach(Pipe pipe_) {
        pipes.add (pipe_);
        Utils.swap (pipes, active, pipes.size () - 1);
        active++;   
    }

    public void terminated(Pipe pipe_) {
        int index = pipes.indexOf (pipe_);

        //  If we are in the middle of multipart message and current pipe
        //  have disconnected, we have to drop the remainder of the message.
        if (index == current && more)
            dropping = true;

        //  Remove the pipe from the list; adjust number of active pipes
        //  accordingly.
        if (index >= start && index < active) {
            active--;
            Utils.swap (pipes, index, active);
            index = active;
            if (current == active)
                current = start;
        }
        pipes.remove (index);

    }

    public void activated(Pipe pipe_) {
        //  Move the pipe to the list of active pipes.
        Utils.swap (pipes, pipes.indexOf (pipe_), active);
        active++;
    }

    public boolean send(Msg msg_, int flags_) {
        //  Drop the message if required. If we are at the end of the message
        //  switch back to non-dropping mode.
        if (dropping) {

            more = msg_.has_more();
            dropping = more;

            msg_.close ();
            return true;
        }

        Pipe pipe = null;
        while (active > start) {
            pipe = pipes.get(current);
            if (pipe.write (msg_))
                break;

            assert (!more);
            current++;
            start = current;
            if (current == active)
                current = start = active = 0;
        }

        //  If there are no pipes we cannot send the message.
        if (active == start) {
            ZError.errno(ZError.EAGAIN);
            return false;
        }

        //  If it's final part of the message we can fluch it downstream and
        //  continue round-robinning (load balance).
        more = msg_.has_more();
        if (!more) {
            pipe.flush ();
            current++;
            if (current == active)
                current = start;
        }

        return true;
    }

    public boolean has_out() {
        //  If one part of the message was already written we can definitely
        //  write the rest of the message.
        if (more)
            return true;

        while (active > start) {

            //  Check whether a pipe has room for another message.
            if (pipes.get(current).check_write ())
                return true;

            //  Deactivate the pipe.
            current++;
            start = current;
            if (current == active)
                current = start = active = 0;
        }

        return false;
    }
}
