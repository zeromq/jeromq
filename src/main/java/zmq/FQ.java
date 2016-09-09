package zmq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//  Class manages a set of inbound pipes. On receive it performs fair
//  queueing so that senders gone berserk won't cause denial of
//  service for decent senders.
class FQ
{
    //  Inbound pipes.
    private final List<Pipe> pipes;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array.
    private int active;

    //  Index of the next bound pipe to read a message from.
    private int current;

    //  If true, part of a multipart message was already received, but
    //  there are following parts still waiting in the current pipe.
    private boolean more;

    public FQ()
    {
        active = 0;
        current = 0;
        more = false;

        pipes = new ArrayList<Pipe>();
    }

    public void attach(Pipe pipe)
    {
        pipes.add(pipe);
        Collections.swap(pipes, active, pipes.size() - 1);
        active++;
    }

    public void terminated(Pipe pipe)
    {
        final int index = pipes.indexOf(pipe);

        //  Remove the pipe from the list; adjust number of active pipes
        //  accordingly.
        if (index < active) {
            active--;
            Collections.swap(pipes, index, active);
            if (current == active) {
                current = 0;
            }
        }
        pipes.remove(pipe);
    }

    public void activated(Pipe pipe)
    {
        //  Move the pipe to the list of active pipes.
        Collections.swap(pipes, pipes.indexOf(pipe), active);
        active++;
    }

    public Msg recv(ValueReference<Integer> errno)
    {
        return recvPipe(errno, null);
    }

    public Msg recvPipe(ValueReference<Integer> errno, ValueReference<Pipe> pipe)
    {
        //  Round-robin over the pipes to get the next message.
        while (active > 0) {
            //  Try to fetch new message. If we've already read part of the message
            //  subsequent part should be immediately available.
            Msg msg = pipes.get(current).read();
            boolean fetched = msg != null;

            //  Note that when message is not fetched, current pipe is deactivated
            //  and replaced by another active pipe. Thus we don't have to increase
            //  the 'current' pointer.
            if (fetched) {
                if (pipe != null) {
                    pipe.set(pipes.get(current));
                }
                more = msg.hasMore();
                if (!more) {
                    current = (current + 1) % active;
                }
                return msg;
            }

            //  Check the atomicity of the message.
            //  If we've already received the first part of the message
            //  we should get the remaining parts without blocking.
            assert (!more);

            active--;
            Collections.swap(pipes, current, active);
            if (current == active) {
                current = 0;
            }
        }

        //  No message is available. Initialise the output parameter
        //  to be a 0-byte message.
        errno.set(ZError.EAGAIN);
        return null;
    }

    public boolean hasIn()
    {
        //  There are subsequent parts of the partly-read message available.
        if (more) {
            return true;
        }

        //  Note that messing with current doesn't break the fairness of fair
        //  queueing algorithm. If there are no messages available current will
        //  get back to its original value. Otherwise it'll point to the first
        //  pipe holding messages, skipping only pipes with no messages available.
        while (active > 0) {
            if (pipes.get(current).checkRead()) {
                return true;
            }

            //  Deactivate the pipe.
            active--;
            Collections.swap(pipes, current, active);
            if (current == active) {
                current = 0;
            }
        }

        return false;
    }
}
