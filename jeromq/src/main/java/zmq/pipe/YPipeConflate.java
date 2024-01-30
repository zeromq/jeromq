package zmq.pipe;

import zmq.Msg;

// Adapter for dbuffer, to plug it in instead of a queue for the sake
//  of implementing the conflate socket option, which, if set, makes
//  the receiving side to discard all incoming messages but the last one.
//
//  reader_awake flag is needed here to mimic ypipe delicate behaviour
//  around the reader being asleep (see 'c' pointer being NULL in ypipe.hpp)

public class YPipeConflate<T extends Msg> implements YPipeBase<T>
{
    private boolean readerAwake;

    private final DBuffer<T> dbuffer = new DBuffer<>();

    //  Following function (write) deliberately copies uninitialised data
    //  when used with zmq_msg. Initialising the VSM body for
    //  non-VSM messages won't be good for performance.
    @Override
    public void write(final T value, boolean incomplete)
    {
        dbuffer.write(value);
    }

    // There are no incomplete items for conflate ypipe
    @Override
    public T unwrite()
    {
        return null;
    }

    //  Flush is no-op for conflate ypipe. Reader asleep behaviour
    //  is as of the usual ypipe.
    //  Returns false if the reader thread is sleeping. In that case,
    //  caller is obliged to wake the reader up before using the pipe again.
    @Override
    public boolean flush()
    {
        return readerAwake;
    }

    //  Check whether item is available for reading.
    @Override
    public boolean checkRead()
    {
        boolean rc = dbuffer.checkRead();
        if (!rc) {
            readerAwake = false;
        }
        return rc;
    }

    //  Reads an item from the pipe. Returns false if there is no value.
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

        return dbuffer.read();
    }

    //  Applies the function fn to the first elemenent in the pipe
    //  and returns the value returned by the fn.
    //  The pipe mustn't be empty or the function crashes.
    @Override
    public T probe()
    {
        return dbuffer.probe();
    }
}
