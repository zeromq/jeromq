/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2011-2012 Spotify AB
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package zmq;


public class Trie {
    private int refcnt;

    private byte min;
    private int count;
    private int live_nodes;
    
    public static interface ITrieHandler {
        void added(byte[] data, int size, Object arg);
    }
    Trie[] next;
    
    public Trie() {
        min = 0;
        count = 0;
        live_nodes = 0;
        
        refcnt = 0;
        next = null;
    }
    
    //  Add key to the trie. Returns true if this is a new item in the trie
    //  rather than a duplicate.
    public boolean add (byte[] prefix_)
    {
        return add (prefix_, 0);
    }
    
    public boolean add (byte[] prefix_, int start_)
    {
        //  We are at the node corresponding to the prefix. We are done.
        if (prefix_ == null || prefix_.length == start_) {
            ++refcnt;
            return refcnt == 1;
        }

        byte c = prefix_[start_];
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
                min = (byte)Math.min ((byte)min, (byte)c);
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
                ++live_nodes;
                //alloc_assert (next.node);
            }
            return next[0].add (prefix_, start_ + 1);
        }
        else {
            if (next[c - min] == null) {
                next[c - min] = new Trie();
                ++live_nodes;
                //alloc_assert (next.table [c - min]);
            }
            return next[c - min].add (prefix_ , start_ + 1);
        }
    }
    
    private Trie[] realloc(Trie[] table, int size, boolean ended) {
        return Utils.realloc(Trie.class, table, size, ended);
    }
    
    //  Remove key from the trie. Returns true if the item is actually
    //  removed from the trie.
    public boolean rm (byte[] prefix_, int start_)
    {
        if (prefix_ == null || prefix_.length == start_) {
            if (refcnt == 0)
                return false;
            refcnt--;
            return refcnt == 0;
        }

        byte c = prefix_[ start_ ];
        if (count == 0 || c < min || c >= min + count)
            return false;

        Trie next_node =
            count == 1 ? next[0] : next[c - min];

        if (next_node == null)
            return false;

        boolean ret = next_node.rm (prefix_ , start_ + 1);
        if (next_node.is_redundant ()) {
            //delete next_node;
            assert (count > 0);

            if (count == 1) {
                next = null;
                count = 0;
                --live_nodes;
                assert (live_nodes == 0);
            }
            else {
                next[c - min] = null ; 
                assert (live_nodes > 1);
                --live_nodes;

                //  Compact the table if possible
                if (live_nodes == 1) {
                    //  If there's only one live node in the table we can
                    //  switch to using the more compact single-node
                    //  representation
                    Trie node = null;
                    for (int i = 0; i < count; ++i) {
                        if (next[i] != null) {
                            node = next[i];
                            min = (byte)(i + min);
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
                    byte new_min = min;
                    for (int i = 1; i < count; ++i) {
                        if (next[i] != null) {
                            new_min = (byte) (i + min);
                            break;
                        }
                    }
                    assert (new_min != min);

                    assert (new_min > min);
                    assert (count > new_min - min);
                    count = count - (new_min - min);
                    
                    next = realloc(next, count, true);

                    min = new_min;
                }
                else if (c == min + count - 1) {
                    //  We can compact the table "from the right"
                    int new_count = count;
                    for (int i = 1; i < count; ++i) {
                        if (next[count - 1 - i] != null) {
                            new_count = count - i;
                            break;
                        }
                    }
                    assert (new_count != count);
                    count = new_count;

                    next = realloc(next, count, false);
                }
            }
        }

        return ret;
    }
    
    //  Check whether particular key is in the trie.
    public boolean check (byte[] data_)
    {
        //  This function is on critical path. It deliberately doesn't use
        //  recursion to get a bit better performance.
        Trie current = this;
        int start = 0;
        while (true) {

            //  We've found a corresponding subscription!
            if (current.refcnt > 0)
                return true;

            //  We've checked all the data and haven't found matching subscription.
            if (data_.length == start)
                return false;

            //  If there's no corresponding slot for the first character
            //  of the prefix, the message does not match.
            byte c = data_[start];
            if (c < current.min || c >= current.min + current.count)
                return false;

            //  Move to the next character.
            if (current.count == 1)
                current = current.next[0];
            else {
                current = current.next[c - current.min];
                if (current == null)
                    return false;
            }
            start++;
        }
    }
    
    //  Apply the function supplied to each subscription in the trie.
    public void apply(ITrieHandler func, Object arg_) {
        apply_helper(null, 0, 0, func, arg_ );
    }

    private void apply_helper(byte[] buff_, int buffsize_, int maxbuffsize_, ITrieHandler func_,
            Object arg_) {
        //  If this node is a subscription, apply the function.
        if (refcnt > 0)
            func_.added (buff_, buffsize_, arg_);

        //  Adjust the buffer.
        if (buffsize_  >= maxbuffsize_) {
            maxbuffsize_ = buffsize_  + 256;
            buff_ = Utils.realloc (buff_, maxbuffsize_);
            assert (buff_!=null);
        }

        //  If there are no subnodes in the trie, return.
        if (count == 0)
            return;

        //  If there's one subnode (optimisation).
        if (count == 1) {
            buff_ [buffsize_] = min;
            buffsize_++;
            next[0].apply_helper (buff_, buffsize_, maxbuffsize_, func_, arg_);
            return;
        }
        
        //  If there are multiple subnodes.
        for (int c = 0; c != count; c++) {
            buff_ [buffsize_] = (byte) (min + c);
            if (next[c] != null)
                next[c].apply_helper (buff_, buffsize_ + 1, maxbuffsize_,
                    func_, arg_);
        }
    }


    private boolean is_redundant ()
    {
        return refcnt == 0 && live_nodes == 0;
    }




}
