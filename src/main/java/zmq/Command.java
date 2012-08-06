package zmq;


public class Command {

    private final ZObject destination;
    private final Type type;
    
    public enum Type {
        stop,
        plug,
        own,
        attach,
        bind,
        activate_read,
        activate_write,
        hiccup,
        pipe_term,
        pipe_term_ack,
        term_req,
        term,
        term_ack,
        reap,
        reaped,
        done        
    }
    
    long msgs_read;
    Pipe pipe;
    Own object;
    IEngine engine;
    Object hiccup_pipe;
    int linger;
    SocketBase socket;

    public Command (ZObject destination, Type type) {
        this.destination = destination;
        this.type = type;
    }
    
    public ZObject destination() {
        return destination;
    }

    public Type type() {
        return type;
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + type + ", " + destination + "]";
    }

}
