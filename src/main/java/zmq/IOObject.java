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

//  Simple base class for objects that live in I/O threads.
//  It makes communication with the poller object easier and
//  makes defining unneeded event handlers unnecessary.

public class IOObject implements IPollEvents
{
    private Poller poller;
    private IPollEvents handler;

    public IOObject(IOThread ioThread)
    {
        if (ioThread != null) {
            plug(ioThread);
        }
    }

    //  When migrating an object from one I/O thread to another, first
    //  unplug it, then migrate it, then plug it to the new thread.

    public void plug(IOThread ioThread)
    {
        assert (ioThread != null);
        assert (poller == null);

        //  Retrieve the poller from the thread we are running in.
        poller = ioThread.getPoller();
    }

    public void unplug()
    {
        assert (poller != null);

        //  Forget about old poller in preparation to be migrated
        //  to a different I/O thread.
        poller = null;
        handler = null;
    }

    public final void addHandle(SelectableChannel handle)
    {
        poller.addHandle(handle, this);
    }

    public final void removeHandle(SelectableChannel handle)
    {
        poller.removeHandle(handle);
    }

    public final void setPollIn(SelectableChannel handle)
    {
        poller.setPollIn(handle);
    }

    public final void setPollOut(SelectableChannel handle)
    {
        poller.setPollOut(handle);
    }

    public final void setPollConnect(SelectableChannel handle)
    {
        poller.setPollConnect(handle);
    }

    public final void setPollAccept(SelectableChannel handle)
    {
        poller.setPollAccept(handle);
    }

    public final void resetPollIn(SelectableChannel handle)
    {
        poller.resetPollOn(handle);
    }

    public final void resetPollOut(SelectableChannel handle)
    {
        poller.resetPollOut(handle);
    }

    @Override
    public final void inEvent()
    {
        handler.inEvent();
    }

    @Override
    public final void outEvent()
    {
        handler.outEvent();
    }

    @Override
    public final void connectEvent()
    {
        handler.connectEvent();
    }

    @Override
    public final void acceptEvent()
    {
        handler.acceptEvent();
    }

    @Override
    public final void timerEvent(int id)
    {
        handler.timerEvent(id);
    }

    public final void addTimer(long timeout, int id)
    {
        poller.addTimer(timeout, this, id);
    }

    public final void setHandler(IPollEvents handler)
    {
        this.handler = handler;
    }

    public void cancelTimer(int id)
    {
        poller.cancelTimer(this, id);
    }
}
