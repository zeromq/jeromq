package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Simple message queuing broker
 * Same as request-reply broker but using QUEUE device.
 */
public class msgqueue {

    public static void main(String[] args) {
        //  Prepare our context and sockets
        ZeroMQContext context = new ZeroMQContext();

        //  Socket facing clients
        Socket frontend = context.createSocket(SocketType.ROUTER);
        frontend.bind("tcp://*:5559");

        //  Socket facing services
        Socket backend = context.createSocket(SocketType.DEALER);
        backend.bind("tcp://*:5560");

        //  Start the proxy
        context.startProxy(frontend, backend);

        //  We never get here but clean up anyhow
        context.terminate();
    }
}
