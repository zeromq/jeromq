/*
    Copyright (c) 2012 Spotify AB
    Copyright (c) 2012 Other contributors as noted in the AUTHORS file

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

import java.net.SocketAddress;

public class Address {

    public static interface IZAddress {

        String toString(String addr_);
        void resolve(String name_, boolean local_, boolean ip4only_);
        SocketAddress address();
    };
    
    final private String protocol;
    final private String address;
    
    private IZAddress resolved;
    
    public Address(final String protocol, final String address) {
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
        
        return null;
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
