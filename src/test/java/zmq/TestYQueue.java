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

public class TestYQueue {

    @Test
    public void testReuse() {
        // yqueue has a first empty entry
        YQueue<Msg> p = new YQueue<Msg>(Msg.class, 3, true);
        
        p.push();
        
        Msg m1 = new Msg(1);
        Msg m2 = new Msg(2);
        Msg m3 = new Msg(3);
        Msg m4 = new Msg(4);
        Msg m5 = new Msg(5);
        Msg m6 = new Msg(6);
        Msg m7 = new Msg(7);
        m7.put("1234567".getBytes(),0);

        p.back(m1); 
        Msg front = p.front();
        assertThat(front.size(), is(1));

        p.push(); 
        p.back(m2); p.push(); // might allocated new chunk
        p.back(m3); p.push();
        p.pop();
        p.pop();
        p.pop(); // offer the old chunk

        p.back(m4); p.push();
        p.back(m5); p.push();// might reuse the old chunk
        p.back(m6); p.push();
        p.back(m7); p.push();
        
        assertThat(front.size(), is(7));
        assertThat(front.data().length, is(7));
    }
}
