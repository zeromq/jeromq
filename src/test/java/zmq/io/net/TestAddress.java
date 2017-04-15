package zmq.io.net;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestAddress
{
    @Test
    public void testToNotResolvedToString()
    {
        Address addr = new Address("tcp", "google.com:90");
        String saddr = addr.toString();
        assertThat(saddr, is("tcp://google.com:90"));
    }

    @Test
    public void testResolvedToString()
    {
        Address addr = new Address("tcp", "google.com:90");
        addr.resolve(false);
        String resolved = addr.toString();
        assertTrue(resolved.matches("tcp://\\d+\\.\\d+\\.\\d+\\.\\d+:90"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvaid()
    {
        new Address("tcp", "ggglocalhostxxx:90").resolve(false);
    }
}
