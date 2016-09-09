package zmq;

import java.nio.ByteBuffer;

public class Trie
{
    private int refcnt;

    private byte min;
    private int count;
    private int liveNodes;

    public interface ITrieHandler
    {
        void added(byte[] data, int size, Object arg);
    }
    Trie[] next;

    public Trie()
    {
        min = 0;
        count = 0;
        liveNodes = 0;

        refcnt = 0;
        next = null;
    }

    //  Add key to the trie. Returns true if this is a new item in the trie
    //  rather than a duplicate.
    public boolean add(byte[] prefix)
    {
        return add(prefix, 0);
    }

    public boolean add(byte[] prefix, int start)
    {
        //  We are at the node corresponding to the prefix. We are done.
        if (prefix == null || prefix.length == start) {
            ++refcnt;
            return refcnt == 1;
        }

        byte c = prefix[start];
        if (c < min || c >= min + count) {
            //  The character is out of range of currently handled
            //  charcters. We have to extend the table.
            if (count == 0) {
                min = c;
                count = 1;
                next = null;
            }
            else if (count == 1) {
                byte oldc = min;
                Trie oldp = next[0];
                count = (min < c ? c - min : min - c) + 1;
                next = new Trie[count];
                min = (byte) Math.min(min, c);
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
                next = new Trie[1];
                next[0] = new Trie();
                ++liveNodes;
                //alloc_assert (next.node);
            }
            return next[0].add(prefix, start + 1);
        }
        else {
            if (next[c - min] == null) {
                next[c - min] = new Trie();
                ++liveNodes;
                //alloc_assert (next.table [c - min]);
            }
            return next[c - min].add(prefix, start + 1);
        }
    }

    private Trie[] realloc(Trie[] table, int size, boolean ended)
    {
        return Utils.realloc(Trie.class, table, size, ended);
    }

    //  Remove key from the trie. Returns true if the item is actually
    //  removed from the trie.
    public boolean rm(byte[] prefix, int start)
    {
        if (prefix == null || prefix.length == start) {
            if (refcnt == 0) {
                return false;
            }
            refcnt--;
            return refcnt == 0;
        }

        byte c = prefix[start];
        if (count == 0 || c < min || c >= min + count) {
            return false;
        }

        Trie nextNode =
            count == 1 ? next[0] : next[c - min];

        if (nextNode == null) {
            return false;
        }

        boolean ret = nextNode.rm(prefix , start + 1);
        if (nextNode.isRedundant()) {
            //delete next_node;
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
                    Trie node = null;
                    for (int i = 0; i < count; ++i) {
                        if (next[i] != null) {
                            node = next[i];
                            min = (byte) (i + min);
                            break;
                        }
                    }

                    assert (node != null);
                    //free (next.table);
                    next = null;
                    next = new Trie[]{node};
                    count = 1;
                }
                else if (c == min) {
                    //  We can compact the table "from the left"
                    byte newMin = min;
                    for (int i = 1; i < count; ++i) {
                        if (next[i] != null) {
                            newMin = (byte) (i + min);
                            break;
                        }
                    }
                    assert (newMin != min);

                    assert (newMin > min);
                    assert (count > newMin - min);
                    count = count - (newMin - min);

                    next = realloc(next, count, true);

                    min = newMin;
                }
                else if (c == min + count - 1) {
                    //  We can compact the table "from the right"
                    int newCount = count;
                    for (int i = 1; i < count; ++i) {
                        if (next[count - 1 - i] != null) {
                            newCount = count - i;
                            break;
                        }
                    }
                    assert (newCount != count);
                    count = newCount;

                    next = realloc(next, count, false);
                }
            }
        }

        return ret;
    }

    //  Check whether particular key is in the trie.
    public boolean check(ByteBuffer data)
    {
        //  This function is on critical path. It deliberately doesn't use
        //  recursion to get a bit better performance.
        Trie current = this;
        int start = 0;
        while (true) {
            //  We've found a corresponding subscription!
            if (current.refcnt > 0) {
                return true;
            }

            //  We've checked all the data and haven't found matching subscription.
            if (data.remaining() == start) {
                return false;
            }

            //  If there's no corresponding slot for the first character
            //  of the prefix, the message does not match.
            byte c = data.get(start);
            if (c < current.min || c >= current.min + current.count) {
                return false;
            }

            //  Move to the next character.
            if (current.count == 1) {
                current = current.next[0];
            }
            else {
                current = current.next[c - current.min];
                if (current == null) {
                    return false;
                }
            }
            start++;
        }
    }

    //  Apply the function supplied to each subscription in the trie.
    public void apply(ITrieHandler func, Object arg)
    {
        applyHelper(null, 0, 0, func, arg);
    }

    private void applyHelper(byte[] buff, int buffsize, int maxBuffsize, ITrieHandler func,
                             Object arg)
    {
        //  If this node is a subscription, apply the function.
        if (refcnt > 0) {
            func.added(buff, buffsize, arg);
        }

        //  Adjust the buffer.
        if (buffsize  >= maxBuffsize) {
            maxBuffsize = buffsize  + 256;
            buff = Utils.realloc(buff, maxBuffsize);
            assert (buff != null);
        }

        //  If there are no subnodes in the trie, return.
        if (count == 0) {
            return;
        }

        //  If there's one subnode (optimisation).
        if (count == 1) {
            buff [buffsize] = min;
            buffsize++;
            next[0].applyHelper(buff, buffsize, maxBuffsize, func, arg);
            return;
        }

        //  If there are multiple subnodes.
        for (int c = 0; c != count; c++) {
            buff [buffsize] = (byte) (min + c);
            if (next[c] != null) {
                next[c].applyHelper(buff, buffsize + 1, maxBuffsize,
                        func, arg);
            }
        }
    }

    private boolean isRedundant()
    {
        return refcnt == 0 && liveNodes == 0;
    }
}
