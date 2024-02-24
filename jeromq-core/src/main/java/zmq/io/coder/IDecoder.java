package zmq.io.coder;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.util.ValueReference;

public interface IDecoder
{
    interface Step
    {
        enum Result
        {
            MORE_DATA,
            DECODED,
            ERROR;
        }

        Result apply();
    }

    ByteBuffer getBuffer();

    Step.Result decode(ByteBuffer buffer, int size, ValueReference<Integer> processed);

    Msg msg();

    void destroy();
}
