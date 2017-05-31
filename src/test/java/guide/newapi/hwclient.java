package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Hello World client in Java
 * Connects REQ socket to tcp://localhost:5555
 * Sends "Hello" to server, prints the reply ("World")
 */
public class hwclient {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to talk to server
        System.out.println("Connecting to hello world server");

        Socket socket = context.createSocket(SocketType.REQ);
        socket.connect("tcp://localhost:5555");

        for (int i = 0; i != 10; i++) {
            System.out.println("Sending Hello " + i);
            socket.send("Hello");

            String reply = socket.receiveString();
            System.out.println("Received " + reply + " " + i);
        }

        context.terminate();
    }
}
