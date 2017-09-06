package zmq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zmq.pipe.YPipe;
import zmq.util.Errno;

public final class Mailbox implements Closeable
{
    //  The pipe to store actual commands.
    private final YPipe<Command> cpipe;

    //  Signaler to pass signals from writer thread to reader thread.
    private final Signaler signaler;

    //  There's only one thread receiving from the mailbox, but there
    //  is arbitrary number of threads sending. Given that ypipe requires
    //  synchronized access on both of its endpoints, we have to synchronize
    //  the sending side.
    private final Lock sync;

    //  True if the underlying pipe is active, i.e. when we are allowed to
    //  read commands from it.
    private boolean active;

    // mailbox name, for better debugging
    private final String name;

    private final Errno errno;

    public Mailbox(Ctx ctx, String name, int tid)
    {
        this.errno = ctx.errno();
        cpipe = new YPipe<>(Config.COMMAND_PIPE_GRANULARITY.getValue());
        sync = new ReentrantLock();
        signaler = new Signaler(ctx, tid, errno);

        //  Get the pipe into passive state. That way, if the users starts by
        //  polling on the associated file descriptor it will get woken up when
        //  new command is posted.

        Command cmd = cpipe.read();
        assert (cmd == null);
        active = false;

        this.name = name;
    }

    public SelectableChannel getFd()
    {
        return signaler.getFd();
    }

    void send(final Command cmd)
    {
        boolean ok = false;
        sync.lock();
        try {
            cpipe.write(cmd, false);
            ok = cpipe.flush();
        }
        finally {
            sync.unlock();
        }

        if (!ok) {
            signaler.send();
        }
    }

    public Command recv(long timeout)
    {
        Command cmd;
        //  Try to get the command straight away.
        if (active) {
            cmd = cpipe.read();
            if (cmd != null) {
                return cmd;
            }

            //  If there are no more commands available, switch into passive state.
            active = false;
        }

        //  Wait for signal from the command sender.
        boolean rc = signaler.waitEvent(timeout);
        if (!rc) {
            assert (errno.get() == ZError.EAGAIN || errno.get() == ZError.EINTR);
            return null;
        }

        //  Receive the signal.
        signaler.recv();

        //  Switch into active state.
        active = true;

        //  Get a command.
        cmd = cpipe.read();
        assert (cmd != null);

        return cmd;
    }

    @Override
    public void close() throws IOException
    {
        //  TODO: Retrieve and deallocate commands inside the cpipe.

        // Work around problem that other threads might still be in our
        // send() method, by waiting on the mutex before disappearing.
        sync.lock();
        sync.unlock();

        signaler.close();
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + name + "]";
    }
}
