package zmq;

import zmq.pipe.YPipe;
import zmq.util.Errno;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

@Deprecated
public class MailboxSafe implements IMailbox
{
    //  The pipe to store actual commands.
    private final YPipe<Command> cpipe;

    //  Synchronize access to the mailbox from receivers and senders
    private final ReentrantLock sync;

    //  Condition variable to pass signals from writer thread to reader thread.
    private final Condition condition;

    private final ArrayList<Signaler> signalers;

    // mailbox name, for better debugging
    private final String name;

    private final Errno errno;

    public MailboxSafe(Ctx ctx, ReentrantLock sync, String name)
    {
        this.errno = ctx.errno();
        this.cpipe = new YPipe<>(Config.COMMAND_PIPE_GRANULARITY.getValue());
        this.sync = sync;
        this.condition = this.sync.newCondition();
        this.signalers = new ArrayList<>(10);
        this.name = name;

        //  Get the pipe into passive state. That way, if the users starts by
        //  polling on the associated file descriptor it will get woken up when
        //  new command is posted.
        Command cmd = cpipe.read();
        assert (cmd == null);
    }

    public void addSignaler(Signaler signaler)
    {
        this.signalers.add(signaler);
    }

    public void removeSignaler(Signaler signaler)
    {
        this.signalers.remove(signaler);
    }

    public void clearSignalers()
    {
        this.signalers.clear();
    }

    @Override
    public void send(Command cmd)
    {
        sync.lock();
        try {
            cpipe.write(cmd, false);
            boolean ok = cpipe.flush();

            if (!ok) {
                condition.signalAll();

                for (Signaler signaler : signalers) {
                    signaler.send();
                }
            }
        }
        finally {
            sync.unlock();
        }
    }

    @Override
    public Command recv(long timeout)
    {
        Command cmd;

        //  Try to get the command straight away.
        cmd = cpipe.read();
        if (cmd != null) {
            return cmd;
        }

        //  If the timeout is zero, it will be quicker to release the lock, giving other a chance to send a command
        //  and immediately relock it.
        if (timeout == 0) {
            sync.unlock();
            sync.lock();
        }
        else {
            try {
                //  Wait for signal from the command sender.
                if (timeout == -1) {
                    condition.await();
                }
                else {
                    condition.await(timeout, TimeUnit.MILLISECONDS);
                }
            }
            catch (InterruptedException e) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
                errno.set(ZError.EINTR);
                return null;
            }
        }

        //  Another thread may already fetch the command
        cmd = cpipe.read();
        if (cmd == null) {
            errno.set(ZError.EAGAIN);
            return null;
        }

        return cmd;
    }

    @Override
    public void close()
    {
        // Work around problem that other threads might still be in our
        // send() method, by waiting on the mutex before disappearing.
        sync.lock();
        sync.unlock();
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + name + "]";
    }
}
