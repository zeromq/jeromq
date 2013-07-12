/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
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

public class Pub extends XPub {

	public static class PubSession extends XPub.XPubSession {

        public PubSession(IOThread io_thread_, boolean connect_,
                SocketBase socket_, Options options_, Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }

    }

    Pub(Ctx parent_, int tid_, int sid_) {
		super(parent_, tid_, sid_);
		options.type = ZMQ.ZMQ_PUB;
	}
    
    @Override
    protected Msg xrecv()
    {
        //  Messages cannot be received from PUB socket.
        throw new UnsupportedOperationException();
    }


    @Override
    protected boolean xhas_in ()
    {
        return false;
    }

}
