/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2011 250bpm s.r.o.
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

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class TestYQueue
{
    @Test
    public void testReuse()
    {
        // yqueue has a first empty entry
        YQueue<Msg> p = new YQueue<Msg>(3);

        Msg m1 = new Msg(1);
        Msg m2 = new Msg(2);
        Msg m3 = new Msg(3);
        Msg m4 = new Msg(4);
        Msg m5 = new Msg(5);
        Msg m6 = new Msg(6);
        Msg m7 = new Msg(7);
        m7.put("1234567".getBytes(ZMQ.CHARSET));

        p.push(m1);
        assertThat(p.back_pos(), is(1));

        p.push(m2); // might allocated new chunk
        p.push(m3);
        assertThat(p.back_pos(), is(3));

        assertThat(p.front_pos(), is(0));
        p.pop();
        p.pop();
        p.pop(); // offer the old chunk
        assertThat(p.front_pos(), is(3));

        p.push(m4);
        p.push(m5); // might reuse the old chunk
        p.push(m6);

        assertThat(p.back_pos(), is(0));

    }
}
