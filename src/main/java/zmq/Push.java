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

public class Push extends SocketBase
{
    public static class PushSession extends SessionBase
    {
        public PushSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    //  Load balancer managing the outbound pipes.
    private final LB lb;

    public Push(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PUSH;

        lb = new LB();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
    {
        assert (pipe != null);
        lb.attach(pipe);
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        lb.activated(pipe);
    }

    @Override
    protected void xterminated(Pipe pipe)
    {
        lb.terminated(pipe);
    }

    @Override
    public boolean xsend(Msg msg)
    {
        return lb.send(msg, errno);
    }

    @Override
    protected boolean xhasOut()
    {
        return lb.hasOut();
    }
}
