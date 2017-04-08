package zmq.io;

import java.nio.channels.SelectableChannel;

import zmq.poll.IPollEvents;
import zmq.poll.Poller;
import zmq.poll.Poller.Handle;

//  Simple base class for objects that live in I/O threads.
//  It makes communication with the poller object easier and
//  makes defining unneeded event handlers unnecessary.
public class IOObject implements IPollEvents
{
    private final Poller      poller;
    private final IPollEvents handler;

    private boolean alive;

    public IOObject(IOThread ioThread, IPollEvents handler)
    {
        assert (ioThread != null);
        assert (handler != null);

        this.handler = handler;
        //  Retrieve the poller from the thread we are running in.
        poller = ioThread.getPoller();
    }

    //  When migrating an object from one I/O thread to another, first
    //  unplug it, then migrate it, then plug it to the new thread.
    public final void plug()
    {
        alive = true;
    }

    public final void unplug()
    {
        alive = false;
    }

    public final Handle addFd(SelectableChannel fd)
    {
        return poller.addHandle(fd, this);
    }

    public final void removeHandle(Handle handle)
    {
        poller.removeHandle(handle);
    }

    public final void setPollIn(Handle handle)
    {
        poller.setPollIn(handle);
    }

    public final void setPollOut(Handle handle)
    {
        poller.setPollOut(handle);
    }

    public final void setPollConnect(Handle handle)
    {
        poller.setPollConnect(handle);
    }

    public final void setPollAccept(Handle handle)
    {
        poller.setPollAccept(handle);
    }

    public final void resetPollIn(Handle handle)
    {
        poller.resetPollIn(handle);
    }

    public final void resetPollOut(Handle handle)
    {
        poller.resetPollOut(handle);
    }

    @Override
    public final void inEvent()
    {
        assert (alive);
        handler.inEvent();
    }

    @Override
    public final void outEvent()
    {
        assert (alive);
        handler.outEvent();
    }

    @Override
    public final void connectEvent()
    {
        assert (alive);
        handler.connectEvent();
    }

    @Override
    public final void acceptEvent()
    {
        assert (alive);
        handler.acceptEvent();
    }

    @Override
    public final void timerEvent(int id)
    {
        assert (alive);
        handler.timerEvent(id);
    }

    public final void addTimer(long timeout, int id)
    {
        assert (alive);
        poller.addTimer(timeout, this, id);
    }

    public final void cancelTimer(int id)
    {
        assert (alive);
        poller.cancelTimer(this, id);
    }

    @Override
    public String toString()
    {
        return "" + handler;
    }
}
