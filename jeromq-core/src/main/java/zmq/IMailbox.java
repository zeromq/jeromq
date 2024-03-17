package zmq;

import java.io.Closeable;

public interface IMailbox extends Closeable
{
    void send(final Command cmd);

    Command recv(long timeout);
}
