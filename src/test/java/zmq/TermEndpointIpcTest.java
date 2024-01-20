package zmq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TermEndpointIpcTest extends TestTermEndpoint
{
    @Override
    protected String endpointWildcard()
    {
        return "ipc://*";
    }

    @Override
    protected String endpointNormal() throws IOException {
        Path temp = Files.createTempFile("zmq-test-", ".sock");
        Files.delete(temp);
        return "ipc://" + temp;
    }
}
