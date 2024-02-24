package zmq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import zmq.util.Errno;

public class Mailbox implements IMailbox
{
    //  The pipe to store actual commands.
    private final Deque<Command> cpipe;

    //  Signaler to pass signals from writer thread to reader thread.
    // kept it although a ConcurrentLinkedDeque, because the signaler channel is used in many places.
    private final Signaler signaler;

    // mailbox name, for better debugging
    private final String name;

    private final Errno errno;

    public Mailbox(Ctx ctx, String name, int tid)
    {
        this.errno = ctx.errno();
        cpipe = new ConcurrentLinkedDeque<>();
        signaler = new Signaler(ctx, tid, errno);

        this.name = name;
    }

    public SelectableChannel getFd()
    {
        return signaler.getFd();
    }

    @Override
    public void send(final Command cmd)
    {
        cpipe.addLast(cmd);
        signaler.send();
    }

    @Override
    public Command recv(long timeout)
    {
        Command cmd = cpipe.pollFirst();
        while (cmd == null) {
            //  Wait for signal from the command sender.
            boolean rc = signaler.waitEvent(timeout);
            if (!rc) {
                assert (errno.get() == ZError.EAGAIN || errno.get() == ZError.EINTR) : errno.get();
                break;
            }

            //  Receive the signal.
            signaler.recv();
            if (errno.get() == ZError.EINTR) {
                break;
            }

            //  Get a command.
            //  Another thread may already fetch the command, so loop on it
            cmd = cpipe.pollFirst();
        }

        return cmd;
    }

    @Override
    public void close() throws IOException
    {
        signaler.close();
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + name + "]";
    }
}
