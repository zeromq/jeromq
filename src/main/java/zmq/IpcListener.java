package zmq;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;

// fake Unix domain socket
public class IpcListener extends TcpListener {

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

    private final IpcAddress address;
    
    public IpcListener(IOThread io_thread_, SocketBase socket_, final Options options_) {
        super(io_thread_, socket_, options_);
    
        address = new IpcAddress();
    }

    // Get the bound address for use with wildcards
    public String get_address() {
        return address.toString();
    }
    

    //  Set address to listen on.
    public void set_address(String addr_) throws IOException {
        
        address.resolve (addr_, false);
        
        endpoint = address.toString ();
        
        InetSocketAddress sock = (InetSocketAddress) address.address();
        String fake = sock.getAddress().getHostAddress() + ":" + sock.getPort();
        super.set_address(fake);
    }


    
}
