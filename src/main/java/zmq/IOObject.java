package zmq;

import java.nio.channels.SelectableChannel;

public class IOObject implements IPollEvents {

    private Poller poller;
    private IPollEvents handler;
    
    public IOObject(IOThread io_thread_) {
        if (io_thread_ != null) {
            plug(io_thread_);
        }
    }

    public void plug(IOThread io_thread_) {
        assert (io_thread_ != null);
        assert (poller == null);

        //  Retrieve the poller from the thread we are running in.
        poller = io_thread_.get_poller ();    
    }

    public SelectableChannel add_fd (SelectableChannel fd_)
    {
        return poller.add_fd (fd_, this);
    }
    
    public void rm_fd(SelectableChannel handle) {
        poller.rm_fd(handle);
    }
    
    public void set_pollin (SelectableChannel handle_)
    {
        poller.set_pollin (handle_);
    }

    public void set_pollout (SelectableChannel handle_)
    {
        poller.set_pollout (handle_);
    }

    public void set_pollconnect(SelectableChannel handle) {
        poller.set_pollconnect(handle);
    }
    
    public void set_pollaccept(SelectableChannel handle) {
        poller.set_pollaccept(handle);
    }
    
    @Override
    public void in_event() {
        handler.in_event();
    }

    @Override
    public void out_event() {
        handler.out_event();
    }
    
    @Override
    public void connect_event() {
        handler.connect_event();
    }

    @Override
    public void accept_event() {
        handler.accept_event();
    }
    
    @Override
    public void timer_event(int id_) {
        handler.timer_event(id_);
    }
    
    public void add_timer (int timeout_, int id_)
    {
        poller.add_timer (timeout_, this, id_);
    }


    public void set_handler(IPollEvents handler) {
        this.handler = handler;
    }

    public void unplug() {
        assert (poller != null);

        //  Forget about old poller in preparation to be migrated
        //  to a different I/O thread.
        poller = null;
        handler = null;
    }

    public void reset_pollin(SelectableChannel handle) {
        poller.reset_pollin (handle);
    }


    public void reset_pollout(SelectableChannel handle) {
        poller.reset_pollout (handle);
    }



}
