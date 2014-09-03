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

package zmq;

import java.util.ArrayList;
import java.util.List;

class Dist
{
    //  List of outbound pipes.
    //typedef array_t <zmq::pipe_t, 2> pipes_t;
    private final List<Pipe> pipes;

    //  Number of all the pipes to send the next message to.
    private int matching;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array. These are the pipes the messages
    //  can be sent to at the moment.
    private int active;

    //  Number of pipes eligible for sending messages to. This includes all
    //  the active pipes plus all the pipes that we can in theory send
    //  messages to (the HWM is not yet reached), but sending a message
    //  to them would result in partial message being delivered, ie. message
    //  with initial parts missing.
    private int eligible;

    //  True if last we are in the middle of a multipart message.
    private boolean more;

    public Dist()
    {
        matching = 0;
        active = 0;
        eligible = 0;
        more = false;
        pipes = new ArrayList<Pipe>();
    }

    //  Adds the pipe to the distributor object.
    public void attach(Pipe pipe)
    {
        //  If we are in the middle of sending a message, we'll add new pipe
        //  into the list of eligible pipes. Otherwise we add it to the list
        //  of active pipes.
        if (more) {
            pipes.add(pipe);
            //pipes.swap (eligible, pipes.size() - 1);
            Utils.swap(pipes, eligible, pipes.size() - 1);
            eligible++;
        }
        else {
            pipes.add(pipe);
            //pipes.swap (active, pipes.size() - 1);
            Utils.swap(pipes, active, pipes.size() - 1);
            active++;
            eligible++;
        }
    }

    //  Mark the pipe as matching. Subsequent call to sendToMatching
    //  will send message also to this pipe.
    public void match(Pipe pipe)
    {
        int idx = pipes.indexOf(pipe);
        //  If pipe is already matching do nothing.
        if (idx < matching) {
            return;
        }

        //  If the pipe isn't eligible, ignore it.
        if (idx >= eligible) {
            return;
        }

        //  Mark the pipe as matching.
        Utils.swap(pipes, idx, matching);
        matching++;
    }

    //  Mark all pipes as non-matching.
    public void unmatch()
    {
        matching = 0;
    }

    //  Removes the pipe from the distributor object.
    public void terminated(Pipe pipe)
    {
        //  Remove the pipe from the list; adjust number of matching, active and/or
        //  eligible pipes accordingly.
        if (pipes.indexOf(pipe) < matching) {
            Utils.swap(pipes, pipes.indexOf(pipe), matching - 1);
            matching--;
        }
        if (pipes.indexOf(pipe) < active) {
            Utils.swap(pipes, pipes.indexOf(pipe), active - 1);
            active--;
        }
        if (pipes.indexOf(pipe) < eligible) {
            Utils.swap(pipes, pipes.indexOf(pipe), eligible - 1);
            eligible--;
        }
        pipes.remove(pipe);
    }

    //  Activates pipe that have previously reached high watermark.
    public void activated(Pipe pipe)
    {
        //  Move the pipe from passive to eligible state.
        Utils.swap(pipes, pipes.indexOf(pipe), eligible);
        eligible++;

        //  If there's no message being sent at the moment, move it to
        //  the active state.
        if (!more) {
            Utils.swap(pipes, eligible - 1, active);
            active++;
        }
    }

    //  Send the message to all the outbound pipes.
    public boolean send_to_all(Msg msg)
    {
        matching = active;
        return sendToMatching(msg);
    }

    //  Send the message to the matching outbound pipes.
    public boolean sendToMatching(Msg msg)
    {
        //  Is this end of a multipart message?
        boolean msgMore = msg.hasMore();

        //  Push the message to matching pipes.
        distribute(msg);

        //  If mutlipart message is fully sent, activate all the eligible pipes.
        if (!msgMore) {
            active = eligible;
        }

        more = msgMore;

        return true;
    }

    //  Put the message to all active pipes.
    private void distribute(Msg msg)
    {
        //  If there are no matching pipes available, simply drop the message.
        if (matching == 0) {
            return;
        }

        for (int i = 0; i < matching; ++i) {
            if (!write(pipes.get(i), msg)) {
                --i; //  Retry last write because index will have been swapped
            }
        }
        return;
    }

    public boolean hasOut()
    {
        return true;
    }

    //  Write the message to the pipe. Make the pipe inactive if writing
    //  fails. In such a case false is returned.
    private boolean write(Pipe pipe, Msg msg)
    {
        if (!pipe.write(msg)) {
            Utils.swap(pipes, pipes.indexOf(pipe), matching - 1);
            matching--;
            Utils.swap(pipes, pipes.indexOf(pipe), active - 1);
            active--;
            Utils.swap(pipes, active, eligible - 1);
            eligible--;
            return false;
        }
        if (!msg.hasMore()) {
            pipe.flush();
        }
        return true;
    }
}
