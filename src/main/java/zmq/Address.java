package zmq;

import java.net.SocketAddress;

public class Address {

    /*
     *union {
            tcp_address_t *tcp_addr;
            ipc_address_t *ipc_addr;
        } resolved;
     */
    
    public static interface IZAddress {

        String toString(String addr_);
        void resolve(String name_, boolean local_, boolean ip4only_);
        SocketAddress address();
    };
    
    final private String protocol;
    final private String address;
    
    //TcpAddress tcp_addr;
    //IpcAddress ipc_addr;
    
    private IZAddress resolved;
    
    public Address(String protocol, String address) {
        this.protocol = protocol;
        this.address = address;
    }
    
    public String toString(String addr_) {
        if (protocol.equals("tcp")) {
            if (resolved != null) {
                return resolved.toString(addr_);
            }
        }   
        else if (protocol.equals("ipc")) {
            if (resolved != null) {
                return resolved.toString(addr_);
            }
        }   

        if (!protocol.isEmpty() && !address.isEmpty ()) {
            return protocol + "://" + address;
        }
        
        return addr_;
    }

    public String protocol() {
        return protocol;
    }
    
    public String address() {
        return address;
    }

    public IZAddress resolved() {
        return resolved;
    }

    public IZAddress resolved(IZAddress value) {
        resolved = value;
        return resolved;
    }

}
