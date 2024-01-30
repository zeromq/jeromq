package zmq.socket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zmq.Msg;
import zmq.ZError;
import zmq.pipe.Pipe;
import zmq.util.Blob;
import zmq.util.Errno;
import zmq.util.ValueReference;

//  Class manages a set of inbound pipes. On receive it performs fair
//  queuing so that senders gone berserk won't cause denial of
//  service for decent senders.
public class FQ
{
    //  Inbound pipes.
    private final List<Pipe> pipes;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array.
    private int active;

    //  the last pipe we received message from.
    //  NULL when no message has been received or the pipe
    //  has terminated.
    private Pipe lastIn;

    //  Index of the next bound pipe to read a message from.
    private int current;

    //  If true, part of a multipart message was already received, but
    //  there are following parts still waiting in the current pipe.
    private boolean more;

    //  Holds credential after the last_acive_pipe has terminated.
    private Blob savedCredential;

    public FQ()
    {
        active = 0;
        current = 0;
        more = false;

        pipes = new ArrayList<>();
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

        if (lastIn == pipe) {
            savedCredential = lastIn.getCredential();
            lastIn = null;
        }
    }

    public void activated(Pipe pipe)
    {
        //  Move the pipe to the list of active pipes.
        Collections.swap(pipes, pipes.indexOf(pipe), active);
        active++;
    }

    public Msg recv(Errno errno)
    {
        return recvPipe(errno, null);
    }

    public Msg recvPipe(Errno errno, ValueReference<Pipe> pipe)
    {
        //  Round-robin over the pipes to get the next message.
        while (active > 0) {
            //  Try to fetch new message. If we've already read part of the message
            //  subsequent part should be immediately available.
            final Pipe currentPipe = pipes.get(current);
            final Msg msg = currentPipe.read();
            final boolean fetched = msg != null;

            //  Note that when message is not fetched, current pipe is deactivated
            //  and replaced by another active pipe. Thus we don't have to increase
            //  the 'current' pointer.
            if (fetched) {
                if (pipe != null) {
                    pipe.set(currentPipe);
                }
                more = msg.hasMore();
                if (!more) {
                    lastIn = currentPipe;
                    assert (active > 0); // happens when multiple threads receive messages
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

        //  No message is available. Initialize the output parameter
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
        //  queuing algorithm. If there are no messages available current will
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

    public Blob getCredential()
    {
        return lastIn != null ? lastIn.getCredential() : savedCredential;
    }
}
