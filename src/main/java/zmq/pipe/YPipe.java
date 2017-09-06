package zmq.pipe;

import java.util.concurrent.atomic.AtomicInteger;

public class YPipe<T> implements YPipeBase<T>
{
    //  Allocation-efficient queue to store pipe items.
    //  Front of the queue points to the first prefetched item, back of
    //  the pipe points to last un-flushed item. Front is used only by
    //  reader thread, while back is used only by writer thread.
    private final YQueue<T> queue;

    //  Points to the first un-flushed item. This variable is used
    //  exclusively by writer thread.
    private int w;

    //  Points to the first un-prefetched item. This variable is used
    //  exclusively by reader thread.
    private int r;

    //  Points to the first item to be flushed in the future.
    private int f;

    //  The single point of contention between writer and reader thread.
    //  Points past the last flushed item. If it is NULL,
    //  reader is asleep. This pointer should be always accessed using
    //  atomic operations.
    private final AtomicInteger c;

    public YPipe(int qsize)
    {
        queue = new YQueue<>(qsize);
        int pos = queue.backPos();
        f = pos;
        r = pos;
        w = pos;
        c = new AtomicInteger(pos);
    }

    //  Write an item to the pipe.  Don't flush it yet. If incomplete is
    //  set to true the item is assumed to be continued by items
    //  subsequently written to the pipe. Incomplete items are never
    //  flushed down the stream.
    @Override
    public void write(final T value, boolean incomplete)
    {
        //  Place the value to the queue, add new terminator element.
        queue.push(value);

        //  Move the "flush up to here" pointer.
        if (!incomplete) {
            f = queue.backPos();
        }
    }

    //  Pop an incomplete item from the pipe. Returns true is such
    //  item exists, false otherwise.
    @Override
    public T unwrite()
    {
        if (f == queue.backPos()) {
            return null;
        }
        queue.unpush();
        return queue.back();
    }

    //  Flush all the completed items into the pipe. Returns false if
    //  the reader thread is sleeping. In that case, caller is obliged to
    //  wake the reader up before using the pipe again.
    @Override
    public boolean flush()
    {
        //  If there are no un-flushed items, do nothing.
        if (w == f) {
            return true;
        }

        //  Try to set 'c' to 'f'.
        if (!c.compareAndSet(w, f)) {
            //  Compare-and-swap was unsuccessful because 'c' is NULL.
            //  This means that the reader is asleep. Therefore we don't
            //  care about thread-safeness and update c in non-atomic
            //  manner. We'll return false to let the caller know
            //  that reader is sleeping.
            c.set(f);
            w = f;
            return false;
        }

        //  Reader is alive. Nothing special to do now. Just move
        //  the 'first un-flushed item' pointer to 'f'.
        w = f;
        return true;
    }

    //  Check whether item is available for reading.
    @Override
    public boolean checkRead()
    {
        //  Was the value prefetched already? If so, return.
        int h = queue.frontPos();
        if (h != r) {
            return true;
        }

        //  There's no prefetched value, so let us prefetch more values.
        //  Prefetching is to simply retrieve the
        //  pointer from c in atomic fashion. If there are no
        //  items to prefetch, set c to -1 (using compare-and-swap).
        if (c.compareAndSet(h, -1)) {
            // nothing to read, h == r must be the same
        }
        else {
            // something to have been written
            r = c.get();
        }

        //  If there are no elements prefetched, exit.
        //  During pipe's lifetime r should never be NULL, however,
        //  it can happen during pipe shutdown when items
        //  are being deallocated.
        if (h == r || r == -1) {
            return false;
        }

        //  There was at least one value prefetched.
        return true;
    }

    //  Reads an item from the pipe. Returns null if there is no value.
    //  available.
    @Override
    public T read()
    {
        //  Try to prefetch a value.
        if (!checkRead()) {
            return null;
        }

        //  There was at least one value prefetched.
        //  Return it to the caller.

        return queue.pop();
    }

    //  Returns the first element in the pipe without removing it.
    //  The pipe mustn't be empty or the function crashes.
    @Override
    public T probe()
    {
        boolean rc = checkRead();
        assert (rc);

        return queue.front();
    }
}
