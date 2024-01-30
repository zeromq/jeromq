package zmq.poll;

public interface IPollEvents
{
    /**
     * Called by I/O thread when file descriptor is ready for reading.
     */
    default void inEvent()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Called by I/O thread when file descriptor is ready for writing.
     */
    default void outEvent()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Called by I/O thread when file descriptor might be ready for connecting.
     */
    default void connectEvent()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Called by I/O thread when file descriptor is ready for accept.
     */
    default void acceptEvent()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Called when timer expires.
     *
     * @param id the ID of the expired timer.
     */
    default void timerEvent(int id)
    {
        throw new UnsupportedOperationException();
    }
}
