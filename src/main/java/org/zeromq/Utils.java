package org.zeromq;

import java.io.IOException;

public class Utils
{
    private Utils()
    {
    }

    public static int findOpenPort() throws IOException
    {
        return zmq.util.Utils.findOpenPort();
    }
}
