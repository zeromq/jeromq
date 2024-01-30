package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;

import zmq.Msg;

public class TrieTest
{
    private static final Msg prefix = new Msg(new byte[] { 1, 2, 3, 4, 5 });

    @Test
    public void testAddRemoveNodeOnTop()
    {
        Trie trie = new Trie();

        boolean rc = trie.add(null, 0, 0);
        assertThat(rc, is(true));
        rc = trie.rm(null, 0, 0);
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesBelowLevel()
    {
        Trie trie = new Trie();

        boolean rc = trie.add(prefix, 0, prefix.size());
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());
        abv[1] = 0;
        Msg above = new Msg(abv);
        rc = trie.add(above, 0, above.size());
        assertThat(rc, is(true));

        abv[1] = -1;
        above = new Msg(abv);
        rc = trie.add(above, 0, above.size());
        assertThat(rc, is(true));

        rc = trie.rm(prefix, 0, prefix.size());
        assertThat(rc, is(true));

        abv[1] = 0;
        above = new Msg(abv);
        rc = trie.rm(above, 0, above.size());
        assertThat(rc, is(true));

        abv[1] = -1;
        above = new Msg(abv);
        rc = trie.rm(above, 0, above.size());
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesAboveLevel()
    {
        Trie trie = new Trie();

        boolean rc = trie.add(prefix, 0, prefix.size());
        assertThat(rc, is(true));
        byte[] abv = Arrays.copyOf(prefix.data(), prefix.size());
        abv[1] = 3;
        Msg above = new Msg(abv);
        rc = trie.add(above, 0, above.size());
        assertThat(rc, is(true));

        abv[1] = 33;
        above = new Msg(abv);
        rc = trie.add(above, 0, above.size());
        assertThat(rc, is(true));

        rc = trie.rm(prefix, 0, prefix.size());
        assertThat(rc, is(true));

        abv[1] = 3;
        above = new Msg(abv);
        rc = trie.rm(above, 0, above.size());
        assertThat(rc, is(true));

        abv[1] = 33;
        above = new Msg(abv);
        rc = trie.rm(above, 0, above.size());
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveMultiNodesSameLevel()
    {
        Trie trie = new Trie();

        boolean rc = trie.add(prefix, 0, prefix.size());
        assertThat(rc, is(true));
        rc = trie.add(prefix, 0, prefix.size());
        assertThat(rc, is(false));

        rc = trie.rm(prefix, 0, prefix.size());
        assertThat(rc, is(false));
        rc = trie.rm(prefix, 0, prefix.size());
        assertThat(rc, is(true));
    }

    @Test
    public void testAddRemoveOneNode()
    {
        Trie trie = new Trie();

        boolean rc = trie.add(prefix, 0, prefix.size());
        assertThat(rc, is(true));

        rc = trie.rm(prefix, 0, prefix.size());
        assertThat(rc, is(true));
    }
}
