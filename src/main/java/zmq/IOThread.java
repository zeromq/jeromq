package zmq;

import java.nio.channels.SelectableChannel;

public class IOThread extends ZObject implements IPollEvents {

    //  I/O thread accesses incoming commands via this mailbox.
    final private Mailbox mailbox;

    //  Handle associated with mailbox' file descriptor.
    final private SelectableChannel mailbox_handle;

    //  I/O multiplexing is performed using a poller object.
    final private Poller poller;
    
    final String name;
    
    public IOThread(Ctx ctx_, int tid_) {
        super(ctx_, tid_);
        name = "iothread-" + tid_;
        poller = new Poller(name);
        //alloc_assert (poller);

        mailbox = new Mailbox(name);
        mailbox_handle = poller.add_fd (mailbox.get_fd (), this);
        poller.set_pollin (mailbox_handle);
        
    }

    @Override
    public void in_event() {
        //  TODO: Do we want to limit number of commands I/O thread can
        //  process in a single go?

        while (true) {

            //  Get the next command. If there is none, exit.
            Command cmd = mailbox.recv ( 0);
            if (cmd == null)
                break;

            //  Process the command.
            
            cmd.destination().process_command (cmd);
        }

    }

    @Override
    public void out_event() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void connect_event() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void timer_event(int id_) {
        throw new UnsupportedOperationException();
    }

    public Mailbox get_mailbox() {
        return mailbox;
    }

    public void start() {
        poller.start();
    }
    
    public int get_load ()
    {
        return poller.get_load ();
    }

    public Poller get_poller() {
        
        assert (poller != null);
        return poller;
    }
    
    protected void process_stop ()
    {
        poller.rm_fd (mailbox_handle);
        poller.stop ();
    }

    

}
