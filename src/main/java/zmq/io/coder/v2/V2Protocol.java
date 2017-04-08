package zmq.io.coder.v2;

import zmq.io.coder.v1.V1Protocol;

public interface V2Protocol extends V1Protocol
{
    int VERSION = 2;

    int LARGE_FLAG   = 2;
    int COMMAND_FLAG = 4;

    // make checkstyle not block the release
    @Override
    String toString();
}
