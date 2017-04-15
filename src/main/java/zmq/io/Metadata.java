package zmq.io;

import java.util.Objects;
import java.util.Properties;

public class Metadata
{
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
        return Objects.hashCode(dictionary);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Metadata) {
            Metadata other = (Metadata) obj;
            return dictionary.equals(other.dictionary);
        }
        return false;
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
