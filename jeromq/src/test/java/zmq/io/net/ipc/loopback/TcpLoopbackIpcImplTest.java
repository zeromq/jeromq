package zmq.io.net.ipc.loopback;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.io.net.ipc.IpcImpl;

public class TcpLoopbackIpcImplTest
{
    @Test
    public void testTcpLoopbackImplIsSelected()
    {
        assertThat(IpcImpl.get(), instanceOf(TcpLoopbackIpcImpl.class));
    }

    @Test
    public void testIsImplementedViaTcpLoopback()
    {
        assertThat(IpcImpl.get().isImplementedViaTcpLoopback(), is(true));
    }
}
