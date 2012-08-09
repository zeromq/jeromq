package zmq;


import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestAddress {

    @Test
    public void ToNotResolvedToString() {
        Address addr = new Address("tcp", "localhost:90");
        
        String saddr = addr.toString();
        assertThat(saddr, is("tcp://localhost:90"));
    }
    
    @Test
    public void testResolvedToString() {
        Address addr = new Address("tcp", "google.com:90");
        addr.resolved(new TcpAddress());
        addr.resolved().resolve("google.com:90", false);
        
        String saddr = addr.toString();
        //assertThat(saddr, is("tcp://tf-1in-f101.le100.net:90"));
        System.out.println(saddr);

        addr.resolved(null);
        saddr = addr.toString();
        assertThat(saddr, is("tcp://google.com:90"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvaid() {
        
        Address addr = new Address("tcp", "ggglocalhostxxx:90");
        addr.resolved(new TcpAddress());
        addr.resolved().resolve("ggglocalhostxxx:90", false);
    }
}
