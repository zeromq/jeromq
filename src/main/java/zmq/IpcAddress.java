package zmq;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class IpcAddress implements Address.IZAddress {

    private String name;
    private InetSocketAddress address ;
    
    @Override
    public String toString() {
        if (name == null) {
            return null;
        }
        
        return "ipc://" + name;
    }

    @Override
    public void resolve(String name_, boolean ip4only_) {
        
        name = name_;
        
        int hash = name_.hashCode();
        hash = hash % 55536;
        hash += 10000;
        
        
        try {
            address = new InetSocketAddress(InetAddress.getByName(null), hash);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public SocketAddress address() {
        return address;
    }

}
