package zmq;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Mailbox {

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

    public Mailbox() {
    	cpipe = new YPipe<Command>(Config.command_pipe_granularity);
    	sync = new ReentrantLock();
    	signaler = new Signaler();
    }
    
	public SelectableChannel get_fd ()
	{
	    return signaler.get_fd ();
	}
	
	void send (final Command cmd_)
	{   
	    sync.lock ();
	    cpipe.write (cmd_, false);
	    boolean ok = cpipe.flush ();
	    sync.unlock ();
	    if (!ok)
	        signaler.send ();
	}
	
	Command recv ( int timeout_)
	{
	    Command cmd_ = null;
	    //  Try to get the command straight away.
	    if (active) {
	        cmd_ = cpipe.read ();
	        if (cmd_ != null)
	            return cmd_;

	        //  If there are no more commands available, switch into passive state.
	        active = false;
	        signaler.recv ();
	    }

	    //  Wait for signal from the command sender.
	    int rc = signaler.wait (timeout_);
	    if (rc != 0 && (Errno.get() == Errno.EAGAIN || Errno.get() == Errno.EINTR))
	        return null;

	    //  We've got the signal. Now we can switch into active state.
	    active = true;

	    //  Get a command.
	    Errno.errno_assert (rc == 0);
	    cmd_ = cpipe.read ();
	    assert (cmd_ != null);
	    return cmd_;
	}

}
