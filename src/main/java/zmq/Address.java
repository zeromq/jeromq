package zmq;

public class Address {

    /*
     *union {
            tcp_address_t *tcp_addr;
            ipc_address_t *ipc_addr;
        } resolved;
     */
    
    public static interface IZAddress {

        String toString(String addr_);
        int resolve(String name_, boolean local_, boolean ip4only_);
    };
    
    final String protocol;
    final String address;
    
    //TcpAddress tcp_addr;
    //IpcAddress ipc_addr;
    
    public IZAddress resolved;
    
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

}
