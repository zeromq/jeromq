package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import zmq.Msg;
import zmq.ZObject;
import zmq.pipe.Pipe;
import zmq.socket.pubsub.Mtrie.IMtrieHandler;

public class MTrieTest
{
    private static final class MtrieHandler implements IMtrieHandler
    {
        private final AtomicInteger counter = new AtomicInteger();
        @Override
        public void invoke(Pipe pipe, byte[] data, int size, XPub pub)
        {
            counter.incrementAndGet();
        }
    }

    private Pipe pipe;

    private MtrieHandler handler;

    private static final Msg prefix = new Msg(new byte[] { 1, 2, 3, 4, 5 });

    @Before
    public void setUp()
    {
        pipe = createPipe();
        handler = new MtrieHandler();
    }

    private Pipe createPipe()
    {
        ZObject object = new ZObject(null, 0)
        {
        };
        Pipe[] pair = Pipe.pair(new ZObject[] { object, object }, new int[2], new boolean[2]);
        return pair[0];
    }

    @Test
    public void testAddRemoveNodeOnTop()
    {
        Mtrie mtrie = new Mtrie();

        boolean rc = mtrie.addOnTop(pipe);
        assertThat(rc, is(true));
        rc = mtrie.rm(new Msg(1), pipe);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesBelowLevel()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();
        Pipe third = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());
        abv[1] = 0;
        Msg above = new Msg(abv);
        rc = mtrie.add(above, other);
        assertThat(rc, is(true));

        abv[1] = -1;
        above = new Msg(abv);
        rc = mtrie.add(above, third);
        assertThat(rc, is(true));

        rc = mtrie.rm(prefix, pipe);
        assertThat(rc, is(true));

        abv[1] = 0;
        above = new Msg(abv);
        rc = mtrie.rm(above, other);
        assertThat(rc, is(true));

        abv[1] = -1;
        above = new Msg(abv);
        rc = mtrie.rm(above, third);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesAboveLevel()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();
        Pipe third = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());
        abv[1] = 0;
        Msg above = new Msg(abv);
        rc = mtrie.add(above, other);
        assertThat(rc, is(true));

        abv[1] = 33;
        above = new Msg(abv);
        rc = mtrie.add(above, third);
        assertThat(rc, is(true));

        rc = mtrie.rm(prefix, pipe);
        assertThat(rc, is(true));

        abv[1] = 0;
        above = new Msg(abv);
        rc = mtrie.rm(above, other);
        assertThat(rc, is(true));

        abv[1] = 33;
        above = new Msg(abv);
        rc = mtrie.rm(above, third);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesSameLevel()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        rc = mtrie.add(prefix, other);
        assertThat(rc, is(false));

        rc = mtrie.rm(prefix, pipe);
        assertThat(rc, is(false));
        rc = mtrie.rm(prefix, other);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveOneNode()
    {
        Mtrie mtrie = new Mtrie();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));

        rc = mtrie.rm(prefix, pipe);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveOneNodeWithFunctionCall()
    {
        Mtrie mtrie = new Mtrie();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));

        rc = mtrie.rm(pipe, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    public void testAddRemoveMultiNodesSameLevelWithFunctionCall()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        rc = mtrie.add(prefix, other);
        assertThat(rc, is(false));

        rc = mtrie.rm(pipe, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(0));

        rc = mtrie.rm(other, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    public void testAddRemoveNodeOnTopWithFunctionCall()
    {
        Mtrie mtrie = new Mtrie();

        boolean rc = mtrie.addOnTop(pipe);
        assertThat(rc, is(true));
        rc = mtrie.rm(pipe, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    public void testAddRemoveMultiNodesBelowLevelWithFunctionCall()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();
        Pipe third = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());

        abv[1] = 0;
        Msg above = new Msg(abv);
        rc = mtrie.add(above, other);
        assertThat(rc, is(true));

        abv[1] = -1;
        above = new Msg(abv);
        rc = mtrie.add(above, third);
        assertThat(rc, is(true));

        rc = mtrie.rm(pipe, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(1));

        abv[1] = 0;
        above = new Msg(abv);
        rc = mtrie.rm(other, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(2));

        abv[1] = -1;
        above = new Msg(abv);
        rc = mtrie.rm(third, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(3));
    }

    @Test
    public void testAddRemoveMultiNodesAboveLevelWithFunctionCall()
    {
        Mtrie mtrie = new Mtrie();
        Pipe other = createPipe();
        Pipe third = createPipe();

        boolean rc = mtrie.add(prefix, pipe);
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());

        abv[1] = 3;
        Msg above = new Msg(abv);
        rc = mtrie.add(above, other);
        assertThat(rc, is(true));

        abv[1] = 33;
        above = new Msg(abv);
        rc = mtrie.add(above, third);
        assertThat(rc, is(true));

        rc = mtrie.rm(pipe, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(1));

        abv[1] = 3;
        above = new Msg(abv);
        rc = mtrie.rm(other, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(2));

        abv[1] = 33;
        above = new Msg(abv);
        rc = mtrie.rm(third, handler, null);
        assertThat(rc, is(true));
        assertThat(handler.counter.get(), is(3));
    }
}
