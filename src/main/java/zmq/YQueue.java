package zmq;

import java.lang.reflect.Array;


public class YQueue<T extends IReplaceable> {

    //  Individual memory chunk to hold N elements.
    class Chunk
    {
         final T []values;
         final long[] pos;
         Chunk prev;
         Chunk next;
         
         @SuppressWarnings("unchecked")
         Chunk(Class<T> klass, int size, long memory_ptr, boolean allocate) {
             values = (T[])(Array.newInstance(klass, size));
             pos = new long[size];
             assert values != null;
             prev = next = null;
             for (int i=0; i != values.length; i++) {
                 pos[i] = memory_ptr;
                 memory_ptr++;
                 if (allocate) {
                    try {
                        values[i] = klass.newInstance();
                    } catch (Exception e) {
                        throw new ZException.InstantiationException(e);
                    }
                 }
             }
            
         }
    };

    //  Back position may point to invalid memory if the queue is empty,
    //  while begin & end positions are always valid. Begin position is
    //  accessed exclusively be queue reader (front/pop), while back and
    //  end positions are accessed exclusively by queue writer (back/push).
    private Chunk begin_chunk;
    private int begin_pos;
    private Chunk back_chunk;
    private int back_pos;
    private Chunk end_chunk;
    private int end_pos;
    private Chunk spare_chunk;
    private final Class<T> klass;
    private final int size;
    private boolean allocate;

    //  People are likely to produce and consume at similar rates.  In
    //  this scenario holding onto the most recently freed chunk saves
    //  us from having to call malloc/free.
    
    private long memory_ptr;
    

    public YQueue(Class<T> klass, int size, boolean allocate) {
        
        this.klass = klass;
        this.size = size;
        this.allocate = allocate;
        memory_ptr = 0;
        begin_chunk = new Chunk(klass, size, memory_ptr, allocate);
        memory_ptr += size;
        begin_pos = 0;
        back_pos = 0;
        back_chunk = null;
        spare_chunk = null;
        end_chunk = begin_chunk;
        end_pos = 0;
    }
    
    public long front_pos() {
        return begin_chunk.pos[begin_pos];
    }
    
    public T front() {
        return begin_chunk.values [begin_pos];
    }
    
    
    public T back() {
        return back_chunk.values [back_pos];
    }
    
    
    public long back_pos() {
        return back_chunk.pos [back_pos];
    }

    public T back(T val) {
        if (allocate)
            back_chunk.values[back_pos].replace(val);
        else
            back_chunk.values [back_pos] = val;
        return val;
    }


    public void pop() {
        begin_pos++;
        if (begin_pos == size) {
            Chunk o = begin_chunk;
            begin_chunk = begin_chunk.next;
            begin_chunk.prev = null;
            begin_pos = 0;

            //  'o' has been more recently used than spare_chunk,
            //  so for cache reasons we'll get rid of the spare and
            //  use 'o' as the spare.
            if (spare_chunk == null)
                spare_chunk = o;
        }
    }


    public void push() {
        back_chunk = end_chunk;
        back_pos = end_pos;

        end_pos ++;
        if (end_pos != size)
            return;

        Chunk sc = spare_chunk;
        if (sc != begin_chunk && sc != null) {
            spare_chunk = spare_chunk.next;
            end_chunk.next = sc;
            sc.prev = end_chunk;
        } else {
            end_chunk.next =  new Chunk(klass, size, memory_ptr, allocate);
            memory_ptr += size;
            end_chunk.next.prev = end_chunk;
        }
        end_chunk = end_chunk.next;
        end_pos = 0;
    }


    public void unpush() {
        //  First, move 'back' one position backwards.
        if (back_pos > 0)
            back_pos--;
        else {
            back_pos = size - 1;
            back_chunk = back_chunk.prev;
        }

        //  Now, move 'end' position backwards. Note that obsolete end chunk
        //  is not used as a spare chunk. The analysis shows that doing so
        //  would require free and atomic operation per chunk deallocated
        //  instead of a simple free.
        if (end_pos > 0)
            end_pos--;
        else {
            end_pos = size - 1;
            end_chunk = end_chunk.prev;
            end_chunk.next = null;
        }
    }



}
