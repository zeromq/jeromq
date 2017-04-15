package zmq.poll;

public interface IPollEvents
{
    // Called by I/O thread when file descriptor is ready for reading.
    void inEvent();

    // Called by I/O thread when file descriptor is ready for writing.
    void outEvent();

    // Called by I/O thread when file descriptor might be ready for connecting.
    void connectEvent();

    // Called by I/O thread when file descriptor is ready for accept.
    void acceptEvent();

    // Called when timer expires.
    void timerEvent(int id);
}
