package guide;

import org.zeromq.ZMQ;

//  Report 0MQ version
public class version
{
    public static void main(String[] args)
    {
        String version = ZMQ.getVersionString();
        int fullVersion = ZMQ.getFullVersion();

        System.out.printf("Version string: %s, Version int: %d%n", version, fullVersion
        );
    }
}
