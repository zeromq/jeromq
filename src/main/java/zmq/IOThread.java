/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package zmq;

import java.nio.channels.SelectableChannel;

public class IOThread extends ZObject implements IPollEvents
{
    //  I/O thread accesses incoming commands via this mailbox.
    private final Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    private final SelectableChannel mailboxHandle;

    //  I/O multiplexing is performed using a poller object.
    private final Poller poller;

    final String name;

    public IOThread(Ctx ctx, int tid)
    {
        super(ctx, tid);
        name = "iothread-" + tid;
        poller = new Poller(name);

        mailbox = new Mailbox(name);
        mailboxHandle = mailbox.getFd();
        poller.addHandle(mailboxHandle, this);
        poller.setPollIn(mailboxHandle);
    }

    public void start()
    {
        poller.start();
    }

    public void destroy()
    {
        poller.destroy();
        mailbox.close();
    }

    public void stop()
    {
        sendStop();
    }

    public Mailbox getMailbox()
    {
        return mailbox;
    }

    public int getLoad()
    {
        return poller.getLoad();
    }

    @Override
    public void inEvent()
    {
        //  TODO: Do we want to limit number of commands I/O thread can
        //  process in a single go?

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

    public Poller getPoller()
    {
        assert (poller != null);
        return poller;
    }

    protected void processStop()
    {
        poller.removeHandle(mailboxHandle);

        poller.stop();
    }
}
