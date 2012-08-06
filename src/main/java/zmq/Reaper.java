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
    
    private String name;
    
    public Reaper (Ctx ctx_, int tid_) 
    {
        super(ctx_, tid_);
        sockets = 0;
        terminating = false;
        name = "reaper-" + tid_;
        poller = new Poller(name);
        //alloc_assert (poller);

        mailbox = new Mailbox(name);
        
        mailbox_handle = poller.add_fd (mailbox.get_fd (), this);
        poller.set_pollin (mailbox_handle);
    }
    
    protected void process_reap (SocketBase socket_)
    {   
        //  Add the socket to the poller.
        socket_.start_reaping (poller);

        ++sockets;
    }

    protected void rocess_stop ()
    {
        terminating = true;

        //  If there are no sockets being reaped finish immediately.
        if (sockets == 0) {
            send_done ();
            poller.rm_fd (mailbox_handle);
            poller.stop ();
        }
    }


    @Override
    public void in_event() {

        while (true) {

            //  Get the next command. If there is none, exit.
            Command cmd = mailbox.recv (0);
            System.out.println("Reaper " + cmd);
            if (cmd == null)
                break;
                
            //  Process the command.
            cmd.destination().process_command (cmd);
        }

    }
    
    @Override
    protected void process_stop ()
    {
        terminating = true;

        //  If there are no sockets being reaped finish immediately.
        if (sockets == 0) {
            send_done ();
            poller.rm_fd (mailbox_handle);
            poller.stop ();
        }
    }

    @Override
    protected void process_reaped ()
    {
        --sockets;

        //  If reaped was already asked to terminate and there are no more sockets,
        //  finish immediately.
        if (sockets == 0 && terminating) {
            send_done ();
            poller.rm_fd (mailbox_handle);
            poller.stop ();
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

    public void stop() {
        send_stop ();
    }
}
