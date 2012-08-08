package zmq;

import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicReferenceArray;


@SuppressWarnings("rawtypes")
public class YQueue<T extends IReplaceable> {

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
    private Chunk begin_chunk;
    private int begin_pos;
    private Chunk back_chunk;
    private int back_pos;
    private Chunk end_chunk;
    private int end_pos;
    private final Class<T> klass;
    private final int size;
    private byte allocated;
    private int qid;
    private int front_hash;
    private int back_hash;

    private static int MAX_SHARED_QUEUE = 2;
    //  People are likely to produce and consume at similar rates.  In
    //  this scenario holding onto the most recently freed chunk saves
    //  us from having to call malloc/free.
    
    private static AtomicReferenceArray spare_chunks = new AtomicReferenceArray(MAX_SHARED_QUEUE);
    

    public YQueue(Class<T> klass, int size) {
        this(klass, size, 0);
    }

    public YQueue(Class<T> klass, int size, int qid) {
        
        this.klass = klass;
        this.size = size;
        this.qid = qid;
        begin_chunk = new Chunk(klass, size);
        begin_pos = 0;
        back_pos = 0;
        back_chunk = end_chunk = begin_chunk;
        end_pos = 0;

        back_hash = front_hash = begin_chunk.hashCode();
        allocated = 0;
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
        return front_hash + begin_pos;
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
        return back_hash + back_pos;
    }

    public T back(T val) {
        if (allocated == 2)
            back_chunk.values [back_pos].replace(val);
        else
            back_chunk.values [back_pos] = val;
        return val;
    }


    @SuppressWarnings("unchecked")
    public void pop() {
        begin_pos++;
        if (begin_pos == size) {
            Chunk o = begin_chunk;
            begin_chunk = begin_chunk.next;
            front_hash = begin_chunk.hashCode();
            begin_chunk.prev = null;
            begin_pos = 0;

            //  'o' has been more recently used than spare_chunk,
            //  so for cache reasons we'll get rid of the spare and
            //  use 'o' as the spare.
            spare_chunks.set(qid, o);
        }
    }


    @SuppressWarnings("unchecked")
    public void push() {
        if (back_chunk != end_chunk) {
            back_chunk = end_chunk;
            back_hash = back_chunk.hashCode();
            if (allocated == 1)
                allocated = 2;
        }
        back_pos = end_pos;

        end_pos ++;
        if (end_pos != size)
            return;

        Chunk sc = (Chunk) spare_chunks.getAndSet(qid, null);
        if (sc != null) {
            end_chunk.next = sc;
            sc.prev = end_chunk;
            allocated = 1;
        } else {
            end_chunk.next =  new Chunk(klass, size);
            end_chunk.next.prev = end_chunk;
            allocated = 0;
        }
        end_chunk = end_chunk.next;
        end_pos = 0;
    }


    @SuppressWarnings("unchecked")
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
            spare_chunks.set(qid,end_chunk.next);
            end_chunk.next = null;
        }
    }



}
