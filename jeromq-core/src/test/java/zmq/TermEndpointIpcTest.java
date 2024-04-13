package zmq;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class TermEndpointIpcTest extends TestTermEndpoint
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected String endpointWildcard()
    {
        return "ipc://*";
    }

    @Override
    protected String endpointNormal()
    {
        Path temp = tempFolder.getRoot().toPath().resolve("zmq-test.sock");
        return "ipc://" + temp;
    }
}
