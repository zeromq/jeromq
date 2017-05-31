package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Hello World server in Java
 * Binds REP socket to tcp://*:5555
 * Gets "Hello" from client, replies with "World"
 */
public class hwserver {

    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to talk to clients
        Socket socket = context.createSocket(SocketType.REP);

        socket.bind("tcp://*:5555");

        while (!Thread.currentThread().isInterrupted()) {
            String reply = socket.receiveString();
            System.out.println("Received " + ": [" + reply + "]");

            // Send the response
            socket.send("World");

            // Do some 'work'
            Thread.sleep(1000);
        }

        context.terminate();
    }
}
