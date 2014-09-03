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

public class Pub extends XPub
{
    public static class PubSession extends XPub.XPubSession
    {
        public PubSession(IOThread ioThread, boolean connect,
                SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    public Pub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PUB;
    }

    @Override
    protected Msg xrecv()
    {
        //  Messages cannot be received from PUB socket.
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhasIn()
    {
        return false;
    }
}
