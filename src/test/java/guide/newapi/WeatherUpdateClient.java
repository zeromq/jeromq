package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Weather update client in Java.
 * Connects SUB socket to tcp://localhost:5556.
 * Collects weather updates and finds average temp in zip code.
 */
public class WeatherUpdateClient {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to talk to server
        System.out.println("Collecting updates from weather server");
        Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.connect("tcp://localhost:5556");

        //  Subscribe to zip code, default is NYC, 10001
        String filter = (args.length > 0) ? args[0] : "10001 ";
        subscriber.subscribe(filter);

        //  Process 100 updates
        int updateNumber;
        long totalTemperature = 0;
        for (updateNumber = 0; updateNumber < 100; updateNumber++) {
            //  Use trim to remove the tailing '0' character
            String string = subscriber.receiveString();

            String[] pieces = string.split(" ");
//            int zipCode = Integer.valueOf(pieces[0]);
            int temperature = Integer.valueOf(pieces[1]);
//            int relativeHumidity = Integer.valueOf(pieces[2]);

            totalTemperature += temperature;

        }
        System.out.println("Average temperature for zipcode '" + filter +
                "' was " + (int) (totalTemperature / updateNumber));

        context.terminate();
    }
}
