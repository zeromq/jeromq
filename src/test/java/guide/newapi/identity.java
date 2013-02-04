package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

public class identity {

    public static void main(String[] args) throws InterruptedException {
        ZeroMQContext context = new ZeroMQContext();

        Socket sink = context.createSocket(SocketType.ROUTER);
        sink.bind("inproc://example");

        //  First allow 0MQ to set the identity, [00] + random 4byte
        Socket anonymous = context.createSocket(SocketType.REQ);

        anonymous.connect("inproc://example");
        anonymous.send("ROUTER uses a generated UUID");
        sink.dumpToConsole();

        //  Then set the identity ourself
        Socket identified = context.createSocket(SocketType.REQ);
        identified.setIdentity("Hello");
        identified.connect("inproc://example");
        identified.send("ROUTER socket uses REQ's socket identity");
        sink.dumpToConsole();

        context.terminate();
    }
}
