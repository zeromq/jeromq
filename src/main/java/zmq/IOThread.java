package zmq;

import java.nio.channels.SelectableChannel;

public class IOThread extends ZObject implements IPollEvents {

    //  I/O thread accesses incoming commands via this mailbox.
    final Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    final SelectableChannel mailbox_handle;

    //  I/O multiplexing is performed using a poller object.
    final Poller poller;
    
	public IOThread(Ctx ctx_, int tid_) {
		super(ctx_, tid_);
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
	
	int get_load ()
	{
	    return poller.get_load ();
	}

    public Poller get_poller() {
        
        assert (poller != null);
        return poller;
    }


}
