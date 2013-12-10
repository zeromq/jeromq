/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package zmq;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class TcpAddress implements Address.IZAddress {

    public static class TcpAddressMask extends TcpAddress {
        public boolean match_address(SocketAddress addr_) {
            return address.equals(addr_); 
        }
    }

    protected InetSocketAddress address;
    
    public TcpAddress(String addr_) {
        resolve(addr_, false);
    }
    public TcpAddress() {
    }
    
    @Override
    public String toString() {
        if (address == null) {
            return "";
        }
        
        if (address.getAddress() instanceof Inet6Address) {
            return "tcp://[" + address.getAddress().getHostAddress() + "]:" + address.getPort();
        } else {
            return "tcp://" + address.getAddress().getHostAddress() + ":" + address.getPort();
        }
        
    }
    
    public int getPort(){
        if (address != null)
            return address.getPort();
        return -1;
    }

    //Used after binding to ephemeral port to update ephemeral port (0) to actual port
    protected void updatePort(int port){
        address = new InetSocketAddress(address.getAddress(), port);
    }

    @Override
    public void resolve(String name_, boolean ipv4only_) {
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
            addr_str = addr_str.substring (1, addr_str.length () - 1);

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

        InetAddress addr_net = null;

        if (addr_str.equals("*")) {
            addr_str = "0.0.0.0";
        }
        try {
            for(InetAddress ia: InetAddress.getAllByName(addr_str)) {
                if (ipv4only_ && (ia instanceof Inet6Address)) {
                    continue;
                }
                addr_net = ia;
                break;
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        
        if (addr_net == null) {
            throw new IllegalArgumentException(name_);
        }

        address = new InetSocketAddress(addr_net, port);

    }

    @Override
    public SocketAddress address() {
        return address;
    }

}
