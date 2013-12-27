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
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestAddress {

    @Test
    public void ToNotResolvedToString() {
        Address addr = new Address("tcp", "google.com:90", false);
        String saddr = addr.toString();
        assertThat(saddr, is("tcp://google.com:90"));
    }

    @Test
    public void testResolvedToString() {
        Address addr = new Address("tcp", "google.com:90", false);
        addr.resolve();
        String resolved = addr.toString();
        assertTrue(resolved.matches("tcp://\\d+\\.\\d+\\.\\d+\\.\\d+:90"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvaid() {
        new Address("tcp", "ggglocalhostxxx:90", false).resolve();
    }
}
