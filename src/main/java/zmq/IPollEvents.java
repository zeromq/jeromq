package zmq;

public interface IPollEvents {
    // Called by I/O thread when file descriptor is ready for reading.
    void in_event () ;

    // Called by I/O thread when file descriptor is ready for writing.
    void out_event () ;

    // Called when timer expires.
    void timer_event (int id_) ;
}
