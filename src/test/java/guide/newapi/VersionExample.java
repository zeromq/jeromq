package guide.newapi;

import org.jeromq.api.ZeroMQContext;

/**
 * Report 0MQ version
 */
public class VersionExample {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();
        System.out.println(String.format("Version string: %s, Version int: %d",
                context.getVersionString(), context.getFullVersion()));
    }

}
