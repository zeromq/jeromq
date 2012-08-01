package zmq;

public class IOObject {

    Poller poller;
    
    public IOObject(IOThread io_thread_) {
        if (io_thread_ != null) {
            plug(io_thread_);
        }
    }

    private void plug(IOThread io_thread_) {
        assert (io_thread_ != null);
        assert (poller == null);

        //  Retrieve the poller from the thread we are running in.
        poller = io_thread_.get_poller ();    
    }

}
