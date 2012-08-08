package zmq;

import java.lang.reflect.Array;

public class YQueue<T> {

    //  Individual memory chunk to hold N elements.
    class Chunk
    {
         final T []values;
         Chunk prev;
         Chunk next;
         
         @SuppressWarnings("unchecked")
         Chunk(Class<T> klass,int size) {
             values = (T[])(Array.newInstance(klass, size));
             assert values != null;
             prev = next = null;
         }
    };

    //  Back position may point to invalid memory if the queue is empty,
    //  while begin & end positions are always valid. Begin position is
    //  accessed exclusively be queue reader (front/pop), while back and
    //  end positions are accessed exclusively by queue writer (back/push).
    Chunk begin_chunk;
    int begin_pos;
    Chunk back_chunk;
    int back_pos;
    Chunk end_chunk;
    int end_pos;
    final Class<T> klass;
    final int size;

    //  People are likely to produce and consume at similar rates.  In
    //  this scenario holding onto the most recently freed chunk saves
    //  us from having to call malloc/free.
    volatile Chunk spare_chunk;

    
    public YQueue(Class<T> klass, int size) {
        
        this.klass = klass;
        this.size = size;
        begin_chunk = new Chunk(klass, size);
        //alloc_assert (begin_chunk);
        begin_pos = 0;
        back_chunk = null;
        back_pos = 0;
        end_chunk = begin_chunk;
        end_pos = 0;
        
        spare_chunk = null;
        
    }
    
    /*
    private T newTerminator() {
        try {
            return klass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    */

    public int front_pos() {
        return begin_pos;
    }
    
    public T front() {
        return begin_chunk.values [begin_pos];
    }
    
    
    public T back() {
        T val = back_chunk.values [back_pos];
        //if (val == null) {
        //    val = newTerminator();
        //    back_chunk.values [back_pos] = val;
        //}
        return val;
    }
    
    
    public int back_pos() {
        return back_pos;
    }

    public T back(T val) {
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
        spare_chunk = null;
        if (sc != null) {
            end_chunk.next = sc;
            sc.prev = end_chunk;
        } else {
            end_chunk.next =  new Chunk(klass, size);
            //alloc_assert (end_chunk->next);
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
            //free (end_chunk.next);
            end_chunk.next = null;
        }
    }



}
