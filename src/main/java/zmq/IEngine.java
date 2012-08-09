package zmq;

public interface IEngine {

    void activate_in();

    void plug(IOThread io_thread_, SessionBase session_);

    void activate_out();

}
