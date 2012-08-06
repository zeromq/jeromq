package zmq;

import java.util.ArrayList;
import java.util.List;

public class Dist {
    //  List of outbound pipes.
    //typedef array_t <zmq::pipe_t, 2> pipes_t;
    final List<Pipe> pipes;

    //  Number of all the pipes to send the next message to.
    int matching;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array. These are the pipes the messages
    //  can be sent to at the moment.
    int active;

    //  Number of pipes eligible for sending messages to. This includes all
    //  the active pipes plus all the pipes that we can in theory send
    //  messages to (the HWM is not yet reached), but sending a message
    //  to them would result in partial message being delivered, ie. message
    //  with initial parts missing.
    int eligible;

    //  True if last we are in the middle of a multipart message.
    boolean more;
    
    public Dist() {
    	matching = 0;
    	active = 0;
    	eligible = 0;
    	more = false;
    	pipes = new ArrayList<Pipe>(2);
    }
    
    void attach (Pipe pipe_)
    {   
        //  If we are in the middle of sending a message, we'll add new pipe
        //  into the list of eligible pipes. Otherwise we add it to the list
        //  of active pipes. 
        if (more) {
            pipes.add (pipe_); 
            //pipes.swap (eligible, pipes.size () - 1);
            Array.swap(pipes, eligible, pipes.size () - 1);
            eligible++;
        }
        else {
            pipes.add (pipe_);
            //pipes.swap (active, pipes.size () - 1);
            Array.swap(pipes, active, pipes.size () - 1);
            active++;
            eligible++;
        }
    }

    public void terminated(Pipe pipe_) {
        //  Remove the pipe from the list; adjust number of matching, active and/or
        //  eligible pipes accordingly.
        if (pipes.indexOf (pipe_) < matching)
            matching--;
        if (pipes.indexOf (pipe_) < active)
            active--;
        if (pipes.indexOf (pipe_) < eligible)
            eligible--;
        pipes.remove(pipe_);
    }

}
