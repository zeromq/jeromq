package zmq;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class TcpAddress implements Address.IZAddress {

    private InetSocketAddress address;
    
    @Override
    public String toString(String addr_) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void resolve(String name_, boolean local_, boolean ipv4only_) {
        //  Find the ':' at end that separates address from the port number.
        int delimiter = name_.lastIndexOf(':');
        if (delimiter < 0) {
            throw new IllegalArgumentException(name_);
        }

        //  Separate the address/port.
        String addr_str = name_.substring(0, delimiter); 
        String port_str = name_.substring(delimiter+1);
        
        //  Remove square brackets around the address, if any.
        if (addr_str.length () >= 2 && addr_str.charAt(0) == '[' &&
              addr_str.charAt(addr_str.length () - 1) == ']')
            addr_str = addr_str.substring (1, addr_str.length () - 2);

        int port;
        //  Allow 0 specifically, to detect invalid port error in atoi if not
        if (port_str.equals("*") || port_str.equals("0"))
            //  Resolve wildcard to 0 to allow autoselection of port
            port = 0;
        else {
            //  Parse the port number (0 is not a valid port).
            port = Integer.parseInt(port_str);
            if (port == 0) {
                throw new IllegalArgumentException(name_);
            }
        }
        //  Resolve the IP address.
        /*
        int rc;
        if (local_)
            rc = resolve_interface (addr_str, ipv4only_);
        else
            rc = resolve_hostname (addr_str, ipv4only_);
        if (rc != 0)
            return -1;

        //  Set the port into the address structure.
        if (address ... Inet6Address)
            address.ipv6.sin6_port = htons (port);
        else
            address.ipv4.sin_port = htons (port);
        */
        address = new InetSocketAddress(addr_str, port);
        if (address.getAddress() == null) {
            throw new IllegalArgumentException(name_);
        }

    }

    @Override
    public SocketAddress address() {
        return address;
    }

}
