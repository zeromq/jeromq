package zmq;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestYQueue {

    @Test
    public void testReuse() {
        // yqueue has a first empty entry
        YPipe<Msg> p = new YPipe<Msg>(Msg.class, 3, 0);
        YPipe<Msg> q = new YPipe<Msg>(Msg.class, 3, 0);
        
        p.read();
        q.read();
        
        Msg m1 = new Msg(1);
        Msg m2 = new Msg(2);
        Msg m3 = new Msg(3);
        Msg m4 = new Msg(4);
        Msg m5 = new Msg(5);
        Msg m6 = new Msg(6);
        Msg m7 = new Msg(7);
        m7.put("1234567".getBytes());

        assertThat(m1.size(), is(1));
        p.write(m1, false); 
        p.write(m2, false); // might allocated new chunk
        p.write(m3, false);
        p.flush();
        p.read();
        p.read();
        p.read(); // offer the old chunk

        q.write(m4, false);
        q.write(m5, false); // might reuse the old chunk
        q.write(m6, false); 
        q.write(m7, false); 
        
        assertThat(m1.size(), is(7));
        assertThat(m1.data().limit(), is(7));
    }
}
