package zmq.io.net.ipc;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class UnixDomainSocketIpcImplTest
{
    @Test
    public void testUnixDomainSocketImplIsSelected()
    {
        assertThat(IpcImpl.get(), instanceOf(UnixDomainSocketIpcImpl.class));
    }

    @Test
    public void testIsImplementedViaTcpLoopback()
    {
        assertThat(IpcImpl.get().isImplementedViaTcpLoopback(), is(false));
    }
}
