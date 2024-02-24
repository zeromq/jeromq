package zmq.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;

import org.junit.Test;

import zmq.ZMQ;

public class V0ProtocolTest extends AbstractProtocolVersion
{
    @Test
    public void testFixIssue524() throws IOException, InterruptedException
    {
        for (int idx = 0; idx < REPETITIONS; ++idx) {
            if (idx % 100 == 0) {
                System.out.print(idx + " ");
            }
            testProtocolVersion0short();
        }
        System.out.println();
    }

    @Test(timeout = 2000)
    public void testProtocolVersion0short() throws IOException, InterruptedException
    {
        ByteBuffer raw = ByteBuffer.allocate(11)
                // send unversioned identity message
                // size
                .put((byte) 0x01)
                // flags
                .put((byte) 0);
        // and payload
        raw.put((byte) 8).put((byte) 0).put("abcdefg".getBytes(ZMQ.CHARSET));
        assertProtocolVersion(0, raw, "abcdefg");
    }

    @Test(timeout = 2000)
    public void testProtocolVersion0long() throws IOException, InterruptedException
    {
        ByteBuffer raw = ByteBuffer.allocate(35)
                // send unversioned identity message
                // large message indicator
                .put((byte) 0xff)
                // size
                .put(new byte[7]).put((byte) 9)
                // flags
                .put((byte) 0)
                // identity
                .put("identity".getBytes(ZMQ.CHARSET));

        // and payload
        raw.put((byte) 0xff).put(new byte[7]).put((byte) 8).put((byte) 0).put("abcdefg".getBytes(ZMQ.CHARSET));
        assertProtocolVersion(0, raw, "abcdefg");
    }

    private byte[] assertProtocolVersion(int version, ByteBuffer raw, String payload)
            throws IOException, InterruptedException
    {
        return assertProtocolVersion(version, Collections.singletonList(raw), payload);
    }
}
