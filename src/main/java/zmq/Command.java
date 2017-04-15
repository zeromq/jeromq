package zmq;

//  This structure defines the commands that can be sent between threads.
public class Command
{
    //  Object to process the command.
    final ZObject destination;
    final Type    type;
    final Object  arg;

    public enum Type
    {
        //  Sent to I/O thread to let it know that it should
        //  terminate itself.
        STOP,
        //  Sent to I/O object to make it register with its I/O thread
        PLUG,
        //  Sent to socket to let it know about the newly created object.
        OWN,
        //  Attach the engine to the session. If engine is NULL, it informs
        //  session that the connection have failed.
        ATTACH,
        //  Sent from session to socket to establish pipe(s) between them.
        //  Caller have used inc_seqnum beforehand sending the command.
        BIND,
        //  Sent by pipe writer to inform dormant pipe reader that there
        //  are messages in the pipe.
        ACTIVATE_READ,
        //  Sent by pipe reader to inform pipe writer about how many
        //  messages it has read so far.
        ACTIVATE_WRITE,
        //  Sent by pipe reader to writer after creating a new inpipe.
        //  The parameter is actually of type pipe_t::upipe_t, however,
        //  its definition is private so we'll have to do with void*.
        HICCUP,
        //  Sent by pipe reader to pipe writer to ask it to terminate
        //  its end of the pipe.
        PIPE_TERM,
        //  Pipe writer acknowledges pipe_term command.
        PIPE_TERM_ACK,
        //  Sent by I/O object ot the socket to request the shutdown of
        //  the I/O object.
        TERM_REQ,
        //  Sent by socket to I/O object to start its shutdown.
        TERM,
        //  Sent by I/O object to the socket to acknowledge it has
        //  shut down.
        TERM_ACK,
        //  Transfers the ownership of the closed socket
        //  to the reaper thread.
        REAP,
        //  Closed socket notifies the reaper that it's already deallocated.
        REAPED,
        // TODO V4 provide a description for Command#INPROC_CONNECTED
        INPROC_CONNECTED,
        //  Sent by reaper thread to the term thread when all the sockets
        //  are successfully deallocated.
        DONE
    }

    Command(ZObject destination, Type type)
    {
        this(destination, type, null);
    }

    Command(ZObject destination, Type type, Object arg)
    {
        this.destination = destination;
        this.type = type;
        this.arg = arg;
    }

    public final void process()
    {
        destination.processCommand(this);
    }

    @Override
    public String toString()
    {
        return "Cmd" + "[" + destination + ", " + (destination == null ? "Reaper" : destination.getTid() + ", ") + type
                + (arg == null ? "" : ", " + arg) + "]";
    }
}
