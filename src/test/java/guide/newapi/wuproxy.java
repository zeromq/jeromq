package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Weather proxy device.
 */
public class wuproxy {

    public static void main(String[] args) {
        //  Prepare our context and sockets
        ZeroMQContext context = new ZeroMQContext();

        //  This is where the weather server sits
        Socket frontend = context.createSocket(SocketType.SUB);
        frontend.connect("tcp://192.168.55.210:5556");

        //  This is our public endpoint for subscribers
        Socket backend = context.createSocket(SocketType.PUB);
        backend.bind("tcp://10.1.1.0:8100");

        //  Subscribe on everything
        frontend.subscribe("");

        //  Run the proxy until the user interrupts us
        context.startProxy(frontend, backend);

        context.terminate();
    }
}
