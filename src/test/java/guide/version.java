package guide;

import org.zeromq.ZMQ;


public class version {

    public static void main(String[] args) {
        System.out.println(String.format("Version string: %s, Version int: %d",
                ZMQ.getVersionString(),
                ZMQ.getFullVersion()));
    }

}
