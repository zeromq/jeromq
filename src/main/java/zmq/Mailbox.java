package zmq;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mailbox {

    Logger LOG = LoggerFactory.getLogger(Mailbox.class);
    
    //  The pipe to store actual commands.
    //typedef ypipe_t <command_t, command_pipe_granularity> cpipe_t;
    final YPipe<Command> cpipe;

    //  Signaler to pass signals from writer thread to reader thread.
    final Signaler signaler;

    //  There's only one thread receiving from the mailbox, but there
    //  is arbitrary number of threads sending. Given that ypipe requires
    //  synchronised access on both of its endpoints, we have to synchronise
    //  the sending side.
    final Lock sync;

    //  True if the underlying pipe is active, ie. when we are allowed to
    //  read commands from it.
    boolean active;
    
    // mailbox name, for better debugging
    final String name;

    public Mailbox(String name_) {
        cpipe = new YPipe<Command>(Command.class, Config.command_pipe_granularity);
        sync = new ReentrantLock();
        signaler = new Signaler();
        
        Command cmd = cpipe.read ();
        assert (cmd == null);
        active = false;
        
        name = name_;
    }
    
    public void close () {
        signaler.close();
    }
    
    public SelectableChannel get_fd ()
    {
        return signaler.get_fd ();
    }
    
    void send (final Command cmd_)
    {   
        boolean ok = false;
        sync.lock ();
        try {
            cpipe.write (cmd_, false);
            ok = cpipe.flush ();
        } finally {
            sync.unlock ();
        }
        
        if (LOG.isDebugEnabled())
            LOG.debug( "{} -> {} signal {}", new Object[] { Thread.currentThread().getName(), cmd_, !ok });
        
        if (!ok) {
            signaler.send ();
        }
    }
    
    Command recv (long timeout_)
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

    @Override
    public String toString() {
        return super.toString() + "[" + name + "]";
    }
}
