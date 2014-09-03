/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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

public class TcpAddress implements Address.IZAddress
{
    public static class TcpAddressMask extends TcpAddress
    {
        public boolean matchAddress(SocketAddress addr)
        {
            return address.equals(addr);
        }
    }

    protected InetSocketAddress address;

    public TcpAddress(String addr)
    {
        resolve(addr, false);
    }

    public TcpAddress()
    {
    }

    @Override
    public String toString()
    {
        if (address == null) {
            return "";
        }

        if (address.getAddress() instanceof Inet6Address) {
            return "tcp://[" + address.getAddress().getHostAddress() + "]:" + address.getPort();
        }
        else {
            return "tcp://" + address.getAddress().getHostAddress() + ":" + address.getPort();
        }
    }

    public int getPort()
    {
        if (address != null) {
            return address.getPort();
        }
        return -1;
    }

    //Used after binding to ephemeral port to update ephemeral port (0) to actual port
    protected void updatePort(int port)
    {
        address = new InetSocketAddress(address.getAddress(), port);
    }

    @Override
    public void resolve(String name, boolean ipv4only)
    {
        //  Find the ':' at end that separates address from the port number.
        int delimiter = name.lastIndexOf(':');
        if (delimiter < 0) {
            throw new IllegalArgumentException(name);
        }

        //  Separate the address/port.
        String addrStr = name.substring(0, delimiter);
        String portStr = name.substring(delimiter + 1);

        //  Remove square brackets around the address, if any.
        if (addrStr.length() >= 2 && addrStr.charAt(0) == '[' &&
              addrStr.charAt(addrStr.length() - 1) == ']') {
            addrStr = addrStr.substring(1, addrStr.length() - 1);
        }

        int port;
        //  Allow 0 specifically, to detect invalid port error in atoi if not
        if (portStr.equals("*") || portStr.equals("0")) {
            //  Resolve wildcard to 0 to allow autoselection of port
            port = 0;
        }
        else {
            //  Parse the port number (0 is not a valid port).
            port = Integer.parseInt(portStr);
            if (port == 0) {
                throw new IllegalArgumentException(name);
            }
        }

        InetAddress addrNet = null;

        if (addrStr.equals("*")) {
            addrStr = "0.0.0.0";
        }
        try {
            for (InetAddress ia : InetAddress.getAllByName(addrStr)) {
                if (ipv4only && (ia instanceof Inet6Address)) {
                    continue;
                }
                addrNet = ia;
                break;
            }
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }

        if (addrNet == null) {
            throw new IllegalArgumentException(name);
        }

        address = new InetSocketAddress(addrNet, port);
    }

    @Override
    public SocketAddress address()
    {
        return address;
    }
}
