package zmq;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestYQueue {

    @Test
    public void testReuse() {
        // yqueue has a first empty entry
        YQueue<Msg> p = new YQueue<Msg>(Msg.class, 3, 0);
        YQueue<Msg> q = new YQueue<Msg>(Msg.class, 3, 0);
        
        p.push();
        q.push();
        
        Msg m1 = new Msg(1);
        Msg m2 = new Msg(2);
        Msg m3 = new Msg(3);
        Msg m4 = new Msg(4);
        Msg m5 = new Msg(5);
        Msg m6 = new Msg(6);
        Msg m7 = new Msg(7);
        m7.put("1234567".getBytes());

        p.back(m1); 
        Msg front = p.front();
        assertThat(front.size(), is(1));

        p.push(); 
        p.back(m2); p.push(); // might allocated new chunk
        p.back(m3); p.push();
        p.pop();
        p.pop();
        p.pop(); // offer the old chunk

        q.back(m4); q.push();
        q.back(m5); q.push();// might reuse the old chunk
        q.back(m6); q.push();
        q.back(m7); q.push();
        
        assertThat(front.size(), is(7));
        assertThat(front.data().remaining(), is(7));
    }
}
