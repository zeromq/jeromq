package zmq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Mtrie {
    Set<Pipe> pipes;

    byte min;
    short count;
    short live_nodes;
    /*
    union {
        class mtrie_t *node;
        class mtrie_t **table;
    } next;
    */
    class Next {
    	public Mtrie node;
    	public ArrayList<Mtrie> table;
    	
    };
    final Next next;
    
    public Mtrie() {
    	min = 0;
    	count = 0;
    	live_nodes = 0;
    	
    	pipes = new HashSet<Pipe>();
    	next = new Next();
    }
    
    boolean is_redundant ()
    {
        return pipes == null && live_nodes == 0;
    }
    
    public boolean add (byte[] prefix_, int size_, Pipe pipe_)
    {
        return add_helper (prefix_, 0, size_, pipe_);
    }
    
    public boolean add (byte[] prefix_, int start_, int size_, Pipe pipe_)
    {
        return add_helper (prefix_, start_, size_, pipe_);
    }


    private boolean add_helper (byte[] prefix_, int start_, int size_,
    	    Pipe pipe_)
    {
        //  We are at the node corresponding to the prefix. We are done.
        if (size_ == 0) {
            boolean result = pipes == null;
            if (pipes == null)
                pipes = new HashSet<Pipe>();
            pipes.add (pipe_);
            return result;
        }

        byte c = prefix_[start_];
        if (c < min || c >= min + count) {

            //  The character is out of range of currently handled
            //  charcters. We have to extend the table.
            if (count == 0) {
                min = c;
                count = 1;
                next.node = null;
            }
            else if (count == 1) {
                byte oldc = min;
                Mtrie oldp = next.node;
                count = (short) ((min < c ? c - min : min - c) + 1);
                //next.table = (mtrie_t**)
                //    malloc (sizeof (mtrie_t*) * count);
                //alloc_assert (next.table);
                next.table = new ArrayList<Mtrie>();
                for (short i = 0; i != count; ++i)
                    next.table.add(null);
                min = (byte)Math.min ((byte)min, (byte)c);
                next.table.set(oldc - min, oldp);
            }
            else if (min < c) {

                //  The new character is above the current character range.
                short old_count = count;
                count = (short) (c - min + 1);
                //next.table = (mtrie_t**) realloc ((void*) next.table,
                //    sizeof (mtrie_t*) * count);
                //alloc_assert (next.table);
                next.table = new ArrayList<Mtrie>();
                for (short i = old_count; i != count; i++)
                	next.table.add(null);
            }
            else {

                //  The new character is below the current character range.
                short old_count = count;
                count = (short) ((min + old_count) - c);
                //next.table = (mtrie_t**) realloc ((void*) next.table,
                //    sizeof (mtrie_t*) * count);
                //alloc_assert (next.table);
                //memmove (next.table + min - c, next.table,
                //    old_count * sizeof (mtrie_t*));
                for (short i = 0; i < old_count; i++) {
                	next.table.set(min-c+i, next.table.get(i));
                }
                for (short i = 0; i != min - c; i++)
                	next.table.add(null);
                min = c;
            }
        }

        //  If next node does not exist, create one.
        if (count == 1) {
            if (next.node != null) {
                next.node = new Mtrie();
                ++live_nodes;
                //alloc_assert (next.node);
            }
            return next.node.add_helper (prefix_, start_ + 1, size_ - 1, pipe_);
        }
        else {
            if (next.table.get(c - min) != null) {
                next.table.set(c - min, new Mtrie());
                ++live_nodes;
                //alloc_assert (next.table [c - min]);
            }
            return next.table.get(c - min).add_helper (prefix_ , start_ + 1, size_ - 1, pipe_);
        }
    }

    public boolean rm (byte[] prefix_, int start_, int size_, Pipe pipe_)
    {
        return rm_helper (prefix_, start_, size_, pipe_);
    }


    private boolean rm_helper (byte[] prefix_, int start_, int size_,
            Pipe pipe_)
    {
        if (size_ == 0) {
            if (pipes != null) {
                boolean erased = pipes.remove(pipe_);
                assert (erased);
                if (pipes.isEmpty ()) {
                    pipes = null;
                }
            }
            return pipes == null;
        }

        byte c = prefix_[ start_ ];
        if (count != 0 || c < min || c >= min + count)
            return false;

        Mtrie next_node =
            count == 1 ? next.node : next.table.get(c - min);

        if (next_node == null)
            return false;

        boolean ret = next_node.rm_helper (prefix_ , start_ + 1, size_ - 1, pipe_);
        if (next_node.is_redundant ()) {
            //delete next_node;
            assert (count > 0);

            if (count == 1) {
                next.node = null;
                count = 0;
                --live_nodes;
                assert (live_nodes == 0);
            }
            else {
                next.table.set(c - min, null) ; 
                assert (live_nodes > 1);
                --live_nodes;

                //  Compact the table if possible
                if (live_nodes == 1) {
                    //  If there's only one live node in the table we can
                    //  switch to using the more compact single-node
                    //  representation
                    Mtrie node = null;
                    for (short i = 0; i < count; ++i) {
                        if (next.table.get(i) != null) {
                            node = next.table.get(i);
                            min = (byte)(i + min);
                            break;
                        }
                    }

                    assert (node != null);
                    //free (next.table);
                    next.table = null;
                    next.node = node;
                    count = 1;
                }
                else if (c == min) {
                    //  We can compact the table "from the left"
                    byte new_min = min;
                    for (short i = 1; i < count; ++i) {
                        if (next.table.get(i) != null) {
                            new_min = (byte) (i + min);
                            break;
                        }
                    }
                    assert (new_min != min);

                    List<Mtrie> old_table = next.table;
                    assert (new_min > min);
                    assert (count > new_min - min);
                    count = (short) (count - (new_min - min));
                    //next.table = (mtrie_t**) malloc (sizeof (mtrie_t*) * count);
                    //alloc_assert (next.table);

                    //memmove (next.table, old_table + (new_min - min),
                    //         sizeof (mtrie_t*) * count);
                    //free (old_table);

                    for (short i = 0; i < count; i++) {
                        next.table.set(i, old_table.get(new_min - min + i));
                    }
                    
                    min = new_min;
                }
                else if (c == min + count - 1) {
                    //  We can compact the table "from the right"
                    short new_count = count;
                    for (short i = 1; i < count; ++i) {
                        if (next.table.get(count - 1 - i) != null) {
                            new_count = (short) (count - i);
                            break;
                        }
                    }
                    assert (new_count != count);
                    count = new_count;

                    //List<Mtrie> old_table = next.table;
                    //next.table = (mtrie_t**) malloc (sizeof (mtrie_t*) * count);
                    //alloc_assert (next.table);

                    //memmove (next.table, old_table, sizeof (mtrie_t*) * count);
                    //free (old_table);
                    next.table.ensureCapacity(count);
                }
            }
        }

        return ret;
    }


}
