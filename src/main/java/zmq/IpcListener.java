package zmq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public class IpcListener extends Own implements IPollEvents {

    //  True, if the undelying file for UNIX domain socket exists.
    private boolean has_file;

    //  Name of the file associated with the UNIX domain address.
    private String filename;

    //  Underlying socket.
    private SelectableChannel s;

    //  Handle corresponding to the listening socket.
    private SelectableChannel handle;

    //  Socket the listerner belongs to.
    private SocketBase socket;

    // String representation of endpoint to bind to
    private String endpoint;

    final IOObject io_object ;
    
    public IpcListener(IOThread io_thread_, SocketBase socket_, final Options options_) {
        super(io_thread_, options_);
        
        io_object = new IOObject(io_thread_);
        has_file = false;
        s = null;
        socket = socket_;
    }

    //  Handlers for incoming commands.
    protected void process_plug () {
        io_object.set_handler(this);
        //  Start polling for incoming connections.
        handle = io_object.add_fd (s);
        io_object.set_pollin (handle);
    }

    protected void process_term (int linger_) {
        io_object.rm_fd (handle);
        close (); 
        super.process_term (linger_);
    }

    //  Set address to listen on.
    public int set_address(String address) {
        throw new UnsupportedOperationException();
    }

    // Get the bound address for use with wildcards
    public String get_address() {
        throw new UnsupportedOperationException();
    }
    
    

    //  Handlers for I/O events.
    public void accept_event () {
        SelectableChannel fd;

        //  If connection was reset by the peer in the meantime, just ignore it.
        //  TODO: Handle specific errors like ENFILE/EMFILE etc.
        try {
            fd = accept ();
        } catch (IOException e) {
            socket.monitor_event (ZMQ.ZMQ_EVENT_ACCEPT_FAILED, endpoint, e);
            return;
        }

        //  Create the engine object for this connection.
        StreamEngine engine = new StreamEngine (fd, options, endpoint);
        assert (engine != null);

        //  Choose I/O thread to run connecter in. Given that we are already
        //  running in an I/O thread, there must be at least one available.
        io_thread_t *io_thread = choose_io_thread (options.affinity);
        zmq_assert (io_thread);

    }

    //  Close the listening socket.

    //  Accept the new connection. Returns the file descriptor of the
    //  newly created connection. The function may return retired_fd
    //  if the connection was dropped while waiting in the listen backlog.
    private SelectableChannel accept ();

    public void close() {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
