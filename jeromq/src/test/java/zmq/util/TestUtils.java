package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Test;
import org.zeromq.TemporaryFolderFinder;

public class TestUtils
{
    public static int port(String endpoint)
    {
        return Integer.parseInt(endpoint.substring(endpoint.lastIndexOf(':') + 1));
    }

    @Test
    public void testUnhash()
    {
        // theoretically up to 65535 but let's be greedy and waste 10 ms
        for (int port = 0; port < 100_000; ++port) {
            String hash = Utils.unhash(port);
            assertThat(hash, notNullValue());
            assertThat(hash.hashCode(), is(port));
        }
    }

    @Test
    public void testRealloc()
    {
        Integer[] src = new Integer[] { 1, 3, 5 };
        Integer[] dest = Utils.realloc(Integer.class, src, 3, true);

        assertThat(src.length, is(3));
        assertThat(src, is(dest));

        dest = Utils.realloc(Integer.class, src, 5, true);

        assertThat(dest.length, is(5));
        assertThat(dest[0], is(1));
        assertThat(dest[1], is(3));
        assertThat(dest[2], is(5));
        assertThat(dest[4], nullValue());

        dest = Utils.realloc(Integer.class, src, 6, false);
        assertThat(dest.length, is(6));
        assertThat(dest[0], nullValue());
        assertThat(dest[1], nullValue());
        assertThat(dest[2], nullValue());
        assertThat(dest[3], is(1));
        assertThat(dest[4], is(3));
        assertThat(dest[5], is(5));

        src = new Integer[] { 1, 3, 5, 7, 9, 11 };
        dest = Utils.realloc(Integer.class, src, 4, false);
        assertThat(dest.length, is(4));
        assertThat(dest[0], is(1));
        assertThat(dest[1], is(3));
        assertThat(dest[2], is(5));
        assertThat(dest[3], is(7));

        dest = Utils.realloc(Integer.class, src, 3, true);
        assertThat(dest.length, is(3));
        assertThat(dest[0], is(7));
        assertThat(dest[1], is(9));
        assertThat(dest[2], is(11));

    }

    @Test
    public void testDeleteFile() throws IOException
    {
        File dir = new File(TemporaryFolderFinder.resolve("testfile"));
        dir.mkdirs();
        File path = File.createTempFile("test", "suffix", dir);

        assertThat(path.exists(), is(true));
        Utils.delete(dir);
        assertThat(path.exists(), is(false));
    }

    @Test
    public void testDeleteDir() throws IOException
    {
        File dir = new File(TemporaryFolderFinder.resolve("testdir"));
        dir.mkdirs();
        File path = File.createTempFile("test", "suffix", dir);

        Utils.delete(dir);
        assertThat(dir.exists(), is(false));
        assertThat(path.exists(), is(false));
    }

    @Test
    public void testDump()
    {
        byte[] array = new byte[10];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        String dump = Utils.dump(buffer, 0, 10);
        assertThat(dump, is("[0,0,0,0,0,0,0,0,0,0,]"));
    }

    @Test
    public void testBytes()
    {
        byte[] array = new byte[10];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        byte[] bytes = Utils.bytes(buffer);
        assertThat(bytes, is(array));
    }

    @Test
    public void testCheckingCorrectArgument()
    {
        try {
            Utils.checkArgument(true, "Error");
        }
        catch (Throwable t) {
            fail("Checking argument should not fail");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCheckingIncorrectArgument()
    {
        Utils.checkArgument(false, "Error");
    }
}
