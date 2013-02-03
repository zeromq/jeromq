package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

import java.util.Random;

/**
 * Weather update server in Java
 * Binds PUB socket to tcp://*:5556
 * Publishes random weather updates
 */
public class WeatherUpdateServer {

    public static void main(String[] args) throws Exception {
        //  Prepare our context and publisher
        ZeroMQContext context = new ZeroMQContext();

        Socket publisher = context.createSocket(SocketType.PUB);
        publisher.bind("tcp://*:5556");
        publisher.bind("ipc://weather");

        //  Initialize random number generator
        Random random = new Random(System.currentTimeMillis());
        while (!Thread.currentThread().isInterrupted()) {
            //  Get values that will fool the boss
            int zipCode = 10000 + random.nextInt(10000);
            int temperature = random.nextInt(215) - 80 + 1;
            int relativeHumidity = random.nextInt(50) + 10 + 1;

            //  Send message to all subscribers
            String update = String.format("%05d %d %d", zipCode, temperature, relativeHumidity);
            publisher.send(update);
        }

        context.terminate();
    }
}