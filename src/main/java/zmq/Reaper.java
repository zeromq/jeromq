package zmq;

import java.nio.channels.SelectableChannel;

public class Reaper extends ZObject implements IPollEvents {

    //  Reaper thread accesses incoming commands via this mailbox.
    final Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    SelectableChannel mailbox_handle;

    //  I/O multiplexing is performed using a poller object.
    final Poller poller;

    //  Number of sockets being reaped at the moment.
    int sockets;

    //  If true, we were already asked to terminate.
    boolean terminating;
    
	public Reaper (Ctx ctx_, int tid_) 
	{
		super(ctx_, tid_);
		sockets = 0;
		terminating = false;
	    poller = new Poller();
	    //alloc_assert (poller);

	    mailbox = new Mailbox();
	    
	    mailbox_handle = poller.add_fd (mailbox.get_fd (), this);
	    poller.set_pollin (mailbox_handle);
	}

	@Override
	public void in_event() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void out_event() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void timer_event(int id_) {
		// TODO Auto-generated method stub
		
	}

	public Mailbox get_mailbox() {
		return mailbox;
	}

	public void start() {
		poller.start();
		
	}

    public void stop() {
        send_stop ();
    }
}
