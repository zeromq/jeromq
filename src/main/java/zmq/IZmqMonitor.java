package zmq;

public interface IZmqMonitor {

    void monitor(SocketBase socket_, int event_, Object[] args_);

}
