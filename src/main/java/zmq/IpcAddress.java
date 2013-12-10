/*
    Copyright (c) 2011 250bpm s.r.o.
    Copyright (c) 2011 Other contributors as noted in the AUTHORS file

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
            return "";
        }
        
        return "ipc://" + name;
    }

    @Override
    public void resolve(String name_, boolean ip4only_) {
        
        name = name_;
        
        int hash = name_.hashCode();
        if (hash < 0)
            hash = -hash;
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
