package zmq;

public interface IEngine {

    void activate_in();
    
    void activate_out();

    void plug(IOThread io_thread_, SessionBase session_);

    void plug_decoder(DecoderBase decoder);

}
