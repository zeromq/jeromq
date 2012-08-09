package guide;

import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class hwserver{
    public static void main(String[] args) throws Exception{
        //  Prepare our context and socket
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket = context.socket(ZMQ.REP);

        System.out.println("Binding hello world server");
        socket.bind ("tcp://*:5555");

        while (true) {

            // Wait for next request from client
            ByteBuffer reply = socket.recv(0);
            System.out.println("Received " + reply.remaining() );
            System.out.println("Received " + ": [" + new String(reply.array(),reply.arrayOffset(),reply.remaining()) + "]");

            Thread.sleep(1000);
            //  Create a "Hello" message.
            //  Ensure that the last byte of our "Hello" message is 0 because
            //  our "Hello World" server is expecting a 0-terminated string:
            String requestString = "Hello" ;
            ByteBuffer request = ByteBuffer.allocate(5);
            request.put(requestString.getBytes());
            //request[request.length-1]=0; //Sets the last byte to 0
            // Send the message
            System.out.println("Sending response " + requestString );
            request.flip();
            socket.send(request, 0);

            //  Get the reply.
        }
    }
}
