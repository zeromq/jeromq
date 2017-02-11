package zmq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class Reaper extends ZObject implements IPollEvents, Closeable
{
    //  Reaper thread accesses incoming commands via this mailbox.
    private final Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    private final SelectableChannel mailboxHandle;

    //  I/O multiplexing is performed using a poller object.
    private final Poller poller;

    //  Number of sockets being reaped at the moment.
    private int socketsReaping;

    //  If true, we were already asked to terminate.
    private final AtomicBoolean terminating = new AtomicBoolean();

    private String name;

    public Reaper(Ctx ctx, int tid)
    {
        super(ctx, tid);
        socketsReaping = 0;
        name = "reaper-" + tid;
        poller = new Poller(name);

        mailbox = new Mailbox(name);

        mailboxHandle = mailbox.getFd();
        poller.addHandle(mailboxHandle, this);
        poller.setPollIn(mailboxHandle);
    }

    @Override
    public void close() throws IOException
    {
        poller.destroy();
        mailbox.close();
    }

    public Mailbox getMailbox()
    {
        return mailbox;
    }

    public void start()
    {
        poller.start();
    }

    public void stop()
    {
        if (!terminating.get()) {
            sendStop();
        }
    }

    @Override
    public void inEvent()
    {
        while (true) {
            //  Get the next command. If there is none, exit.
            Command cmd = mailbox.recv(0);
            if (cmd == null) {
                break;
            }

            //  Process the command.
            cmd.destination().processCommand(cmd);
        }
    }

    @Override
    public void outEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timerEvent(int id)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void processStop()
    {
        terminating.set(true);

        //  If there are no sockets being reaped finish immediately.
        if (socketsReaping == 0) {
            finishTerminating();
        }
    }

    @Override
    protected void processReap(SocketBase socket)
    {
        ++socketsReaping;

        //  Add the socket to the poller.
        socket.startReaping(poller);
    }

    @Override
    protected void processReaped()
    {
        --socketsReaping;

        //  If reaped was already asked to terminate and there are no more sockets,
        //  finish immediately.
        if (socketsReaping == 0 && terminating.get()) {
            finishTerminating();
        }
    }

    private void finishTerminating()
    {
        sendDone();
        poller.removeHandle(mailboxHandle);
        poller.stop();
    }
}
