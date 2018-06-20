package zmq;

import java.io.IOException;
import java.util.UUID;

public class TermEndpointIpcTest extends TestTermEndpoint
{
    @Override
    protected String endpointWildcard()
    {
        return "ipc://*";
    }

    @Override
    protected String endpointNormal() throws IOException
    {
        return "ipc://" + UUID.randomUUID().toString();
    }
}
