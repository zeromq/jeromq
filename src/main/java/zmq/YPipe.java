package zmq;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;


public class YPipe<T> {

    //  Allocation-efficient queue to store pipe items.
    //  Front of the queue points to the first prefetched item, back of
    //  the pipe points to last un-flushed item. Front is used only by
    //  reader thread, while back is used only by writer thread.
    final Deque<T> queue;

    //  Points to the first un-flushed item. This variable is used
    //  exclusively by writer thread.
    T w;

    //  Points to the first un-prefetched item. This variable is used
    //  exclusively by reader thread.
    T r;

    //  Points to the first item to be flushed in the future.
    T f;

    //  The single point of contention between writer and reader thread.
    //  Points past the last flushed item. If it is NULL,
    //  reader is asleep. This pointer should be always accessed using
    //  atomic operations.
    final AtomicReference <T> c;
    
	public YPipe(Config conf) {
		queue = new ArrayDeque<T>(conf.getValue());
		c = new AtomicReference<T>();
	}

	//  Reads an item from the pipe. Returns false if there is no value.
    //  available.
	T read ()
    {
        //  Try to prefetch a value.
        if (!check_read ())
            return null;

        //  There was at least one value prefetched.
        //  Return it to the caller.
        T value_ = queue.poll();
        //queue.pop ();
        return value_;
    }
	
	//  Check whether item is available for reading.
    boolean check_read ()
    {
        //  Was the value prefetched already? If so, return.
        if (queue.peek () != r && r != null)
             return true;

        //  There's no prefetched value, so let us prefetch more values.
        //  Prefetching is to simply retrieve the
        //  pointer from c in atomic fashion. If there are no
        //  items to prefetch, set c to NULL (using compare-and-swap).
        T r = c.get();
        c.compareAndSet (queue.peek (), null);

        //  If there are no elements prefetched, exit.
        //  During pipe's lifetime r should never be NULL, however,
        //  it can happen during pipe shutdown when items
        //  are being deallocated.
        if (queue.peek () == r || r== null)
            return false;

        //  There was at least one value prefetched.
        return true;
    }

    //  Write an item to the pipe.  Don't flush it yet. If incomplete is
    //  set to true the item is assumed to be continued by items
    //  subsequently written to the pipe. Incomplete items are never
    //  flushed down the stream.
    void write (final T value_, boolean incomplete_)
    {
        //  Place the value to the queue, add new terminator element.
        queue.offer(value_);

        //  Move the "flush up to here" poiter.
        if (!incomplete_)
            f = queue.getLast();
    }
    
    //  Flush all the completed items into the pipe. Returns false if
    //  the reader thread is sleeping. In that case, caller is obliged to
    //  wake the reader up before using the pipe again.
    boolean flush ()
    {
        //  If there are no un-flushed items, do nothing.
        if (w == f)
            return true;

        //  Try to set 'c' to 'f'.
        T old = null;
        synchronized (c) {
            old = c.get();
            c.compareAndSet(w, f);
        }
        if (old != w) {

            //  Compare-and-swap was unseccessful because 'c' is NULL.
            //  This means that the reader is asleep. Therefore we don't
            //  care about thread-safeness and update c in non-atomic
            //  manner. We'll return false to let the caller know
            //  that reader is sleeping.
            c.set (f);
            w = f;
            return false;
        }

        //  Reader is alive. Nothing special to do now. Just move
        //  the 'first un-flushed item' pointer to 'f'.
        w = f;
        return true;
    }
    
    //  Pop an incomplete item from the pipe. Returns true is such
    //  item exists, false otherwise.
    T unwrite ()
    {
        if (f == queue.getLast())
            return null;
        return queue.pollLast();
    }


}
