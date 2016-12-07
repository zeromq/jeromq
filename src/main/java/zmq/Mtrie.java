package zmq;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

//Multi-trie. Each node in the trie is a set of pointers to pipes.
public class Mtrie
{
    private Set<Pipe> pipes;

    private int min;
    private int count;
    private int liveNodes;
    private Mtrie[] next;

    public interface IMtrieHandler
    {
        void invoke(Pipe pipe, byte[] data, int size, Object arg);
    }

    public Mtrie()
    {
        min = 0;
        count = 0;
        liveNodes = 0;

        pipes = null;
        next = null;
    }

    public boolean add(byte[] prefix, Pipe pipe)
    {
        return addHelper(prefix, 0, pipe);
    }

    //  Add key to the trie. Returns true if it's a new subscription
    //  rather than a duplicate.
    public boolean add(byte[] prefix, int start, Pipe pipe)
    {
        return addHelper(prefix, start, pipe);
    }

    private boolean addHelper(byte[] prefix, int start, Pipe pipe)
    {
        //  We are at the node corresponding to the prefix. We are done.
        if (prefix == null || prefix.length == start) {
            boolean result = pipes == null;
            if (pipes == null) {
                pipes = new HashSet<Pipe>();
            }
            pipes.add(pipe);
            return result;
        }

        byte c = prefix[start];
        if (c < min || c >= min + count) {
            //  The character is out of range of currently handled
            //  characters. We have to extend the table.
            if (count == 0) {
                min = c;
                count = 1;
                next = null;
            }
            else if (count == 1) {
                int oldc = min;
                Mtrie oldp = next[0];
                count = (min < c ? c - min : min - c) + 1;
                next = new Mtrie[count];
                min = Math.min(min, c);
                next[oldc - min] = oldp;
            }
            else if (min < c) {
                //  The new character is above the current character range.
                count = c - min + 1;
                next = realloc(next, count, true);
            }
            else {
                //  The new character is below the current character range.
                count = (min + count) - c;
                next = realloc(next, count, false);
                min = c;
            }
        }

        //  If next node does not exist, create one.
        if (count == 1) {
            if (next == null) {
                next = new Mtrie[1];
                next[0] = new Mtrie();
                ++liveNodes;
                //alloc_assert (next.node);
            }
            return next[0].addHelper(prefix, start + 1, pipe);
        }
        else {
            if (next[c - min] == null) {
                next[c - min] = new Mtrie();
                ++liveNodes;
                //alloc_assert (next.table [c - min]);
            }
            return next[c - min].addHelper(prefix, start + 1, pipe);
        }
    }

    private Mtrie[] realloc(Mtrie[] table, int size, boolean ended)
    {
        return Utils.realloc(Mtrie.class, table, size, ended);
    }

    //  Remove all subscriptions for a specific peer from the trie.
    //  If there are no subscriptions left on some topics, invoke the
    //  supplied callback function.
    public boolean rm(Pipe pipe, IMtrieHandler func, Object arg, boolean callOnUniq)
    {
        return rmHelper(pipe, new byte[0], 0, 0, func, arg, callOnUniq);
    }

    private boolean rmHelper(Pipe pipe, byte[] buff, int buffsize, int maxBuffSize,
                             IMtrieHandler func, Object arg, boolean callOnUniq)
    {
        //  Remove the subscription from this node.
        if (pipes != null && pipes.remove(pipe)) {
            if (!callOnUniq || pipes.isEmpty()) {
                func.invoke(null, buff, buffsize, arg);
            }

            if (pipes.isEmpty()) {
                pipes = null;
            }
        }

        //  Adjust the buffer.
        if (buffsize >= maxBuffSize) {
            maxBuffSize = buffsize + 256;
            buff = Utils.realloc(buff, maxBuffSize);
        }

        //  If there are no subnodes in the trie, return.
        if (count == 0) {
            return true;
        }

        //  If there's one subnode (optimisation).
        if (count == 1) {
            buff[buffsize] = (byte) min;
            buffsize++;
            next[0].rmHelper(pipe, buff, buffsize, maxBuffSize,
                    func, arg, callOnUniq);

            //  Prune the node if it was made redundant by the removal
            if (next[0].isRedundant()) {
                next = null;
                count = 0;
                --liveNodes;
                assert (liveNodes == 0);
            }
            return true;
        }

        //  If there are multiple subnodes.
        //
        //  New min non-null character in the node table after the removal
        int newMin = min + count - 1;
        //  New max non-null character in the node table after the removal
        int newMax = min;
        for (int c = 0; c != count; c++) {
            buff[buffsize] = (byte) (min + c);
            if (next[c] != null) {
                next[c].rmHelper(pipe, buff, buffsize + 1,
                        maxBuffSize, func, arg, callOnUniq);

                //  Prune redundant nodes from the mtrie
                if (next[c].isRedundant()) {
                    next[c] = null;

                    assert (liveNodes > 0);
                    --liveNodes;
                }
                else {
                    //  The node is not redundant, so it's a candidate for being
                    //  the new min/max node.
                    //
                    //  We loop through the node array from left to right, so the
                    //  first non-null, non-redundant node encountered is the new
                    //  minimum index. Conversely, the last non-redundant, non-null
                    //  node encountered is the new maximum index.
                    if (c + min < newMin) {
                        newMin = c + min;
                    }
                    if (c + min > newMax) {
                        newMax = c + min;
                    }
                }
            }
        }

        assert (count > 1);

        //  Free the node table if it's no longer used.
        if (liveNodes == 0) {
            next = null;
            count = 0;
        }
        //  Compact the node table if possible
        else if (liveNodes == 1) {
            //  If there's only one live node in the table we can
            //  switch to using the more compact single-node
            //  representation
            assert (newMin == newMax);
            assert (newMin >= min && newMin < min + count);
            Mtrie node = next [newMin - min];
            assert (node != null);
            next = null;
            next = new Mtrie[]{node};
            count = 1;
            min = newMin;
        }
        else if (newMin > min || newMax < min + count - 1) {
            assert (newMax - newMin + 1 > 1);

            Mtrie[] oldTable = next;
            assert (newMin > min || newMax < min + count - 1);
            assert (newMin >= min);
            assert (newMax <= min + count - 1);
            assert (newMax - newMin + 1 < count);
            count = newMax - newMin + 1;
            next = new Mtrie[count];

            System.arraycopy(oldTable, (newMin - min), next, 0, count);

            min = newMin;
        }
        return true;
    }

    //  Remove specific subscription from the trie. Return true is it was
    //  actually removed rather than de-duplicated.
    public boolean rm(byte[] prefix, int start, Pipe pipe)
    {
        return rmHelper(prefix, start, pipe);
    }

    private boolean rmHelper(byte[] prefix, int start, Pipe pipe)
    {
        if (prefix == null || prefix.length == start) {
            if (pipes != null) {
                boolean erased = pipes.remove(pipe);
                assert (erased);
                if (pipes.isEmpty()) {
                    pipes = null;
                }
            }
            return pipes == null;
        }

        byte c = prefix[start];
        if (count == 0 || c < min || c >= min + count) {
            return false;
        }

        Mtrie nextNode =
            count == 1 ? next[0] : next[c - min];

        if (nextNode == null) {
            return false;
        }

        boolean ret = nextNode.rmHelper(prefix, start + 1, pipe);
        if (nextNode.isRedundant()) {
            assert (count > 0);

            if (count == 1) {
                next = null;
                count = 0;
                --liveNodes;
                assert (liveNodes == 0);
            }
            else {
                next[c - min] = null;
                assert (liveNodes > 1);
                --liveNodes;

                //  Compact the table if possible
                if (liveNodes == 1) {
                    //  If there's only one live node in the table we can
                    //  switch to using the more compact single-node
                    //  representation
                    int i;
                    for (i = 0; i < count; ++i) {
                        if (next[i] != null) {
                            break;
                        }
                    }

                    assert (i < count);
                    min += i;
                    count = 1;
                    Mtrie old = next [i];
                    next = new Mtrie [] { old };
                }
                else if (c == min) {
                    //  We can compact the table "from the left"
                    int i;
                    for (i = 1; i < count; ++i) {
                        if (next[i] != null) {
                            break;
                        }
                    }

                    assert (i < count);
                    min += i;
                    count -= i;
                    next = realloc(next, count, true);
                }
                else if (c == min + count - 1) {
                    //  We can compact the table "from the right"
                    int i;
                    for (i = 1; i < count; ++i) {
                        if (next[count - 1 - i] != null) {
                            break;
                        }
                    }
                    assert (i < count);
                    count -= i;
                    next = realloc(next, count, false);
                }
            }
        }

        return ret;
    }

    //  Signal all the matching pipes.
    public void match(ByteBuffer data, int size, IMtrieHandler func, Object arg)
    {
        Mtrie current = this;
        int idx = 0;

        while (true) {
            //  Signal the pipes attached to this node.
            if (current.pipes != null) {
                for (Pipe it : current.pipes) {
                    func.invoke(it, null, 0, arg);
                }
            }

            //  If we are at the end of the message, there's nothing more to match.
            if (size == 0) {
                break;
            }

            //  If there are no subnodes in the trie, return.
            if (current.count == 0) {
                break;
            }

            byte c = data.get(idx);
            //  If there's one subnode (optimisation).
            if (current.count == 1) {
                if (c != current.min) {
                    break;
                }
                current = current.next[0];
                idx++;
                size--;
                continue;
            }

            //  If there are multiple subnodes.
            if (c < current.min || c >=
                  current.min + current.count) {
                break;
            }
            if (current.next[c - current.min] == null) {
                break;
            }
            current = current.next[c - current.min];
            idx++;
            size--;
        }
    }

    private boolean isRedundant()
    {
        return pipes == null && liveNodes == 0;
    }
}
