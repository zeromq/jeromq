package zmq.io;

import java.util.Properties;

public class Metadata
{
    public static final String IDENTITY = "Identity";

    public static final String SOCKET_TYPE = "Socket-Type";

    public static final String USER_ID = "User-Id";

    public static final String PEER_ADDRESS = "Peer-Address";

    //  Dictionary holding metadata.
    private final Properties dictionary = new Properties();

    public Metadata()
    {
        super();
    }

    public Metadata(Properties dictionary)
    {
        this.dictionary.putAll(dictionary);
    }

    //  Returns property value or NULL if
    //  property is not found.
    public final String get(String key)
    {
        return dictionary.getProperty(key);
    }

    public final void set(String key, String value)
    {
        dictionary.setProperty(key, value);
    }

    @Override
    public int hashCode()
    {
        return dictionary.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Metadata)) {
            return false;
        }
        Metadata that = (Metadata) other;
        return this.dictionary.equals(that.dictionary);
    }

    public final void set(Metadata zapProperties)
    {
        dictionary.putAll(zapProperties.dictionary);
    }

    public final boolean isEmpty()
    {
        return dictionary.isEmpty();
    }

    @Override
    public String toString()
    {
        return "Metadata=" + dictionary;
    }
}
