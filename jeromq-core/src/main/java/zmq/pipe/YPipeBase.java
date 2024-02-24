package zmq.pipe;

public interface YPipeBase<T>
{
    //  Write an item to the pipe.  Don't flush it yet. If incomplete is
    //  set to true the item is assumed to be continued by items
    //  subsequently written to the pipe. Incomplete items are never
    //  flushed down the stream.
    void write(final T value, boolean incomplete);

    //  Pop an incomplete item from the pipe. Returns true is such
    //  item exists, false otherwise.
    T unwrite();

    //  Flush all the completed items into the pipe. Returns false if
    //  the reader thread is sleeping. In that case, caller is obliged to
    //  wake the reader up before using the pipe again.
    boolean flush();

    //  Check whether item is available for reading.
    boolean checkRead();

    //  Reads an item from the pipe. Returns false if there is no value.
    //  available.
    T read();

    //  Applies the function fn to the first elemenent in the pipe
    //  and returns the value returned by the fn.
    //  The pipe mustn't be empty or the function crashes.
    T probe();
}
