package zmq.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import zmq.Msg;
import zmq.ZError;
import zmq.ZMQ;
import zmq.util.Wire;

public class Metadata
{
    /**
     * Call backs during parsing process
     */
    public interface ParseListener
    {
        /**
         * Called when a property has been parsed.
         * @param name the name of the property.
         * @param value the value of the property.
         * @param valueAsString the value in a string representation.
         * @return 0 to continue the parsing process, any other value to interrupt it.
         */
        int parsed(String name, byte[] value, String valueAsString);
    }

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

    public final Set<String> keySet()
    {
        return dictionary.stringPropertyNames();
    }

    public final void remove(String key)
    {
        dictionary.remove(key);
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

    public final byte[] bytes()
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(size());
        try {
            write(stream);
            return stream.toByteArray();
        }
        catch (IOException e) {
            return new byte[0];
        }
        finally {
            try {
                stream.close();
            }
            catch (IOException e) {
            }
        }
    }

    private int size()
    {
        int size = 0;
        for (Entry<Object, Object> entry : dictionary.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();

            size += 1;
            size += key.length();
            size += 4;
            size += value.length();
        }
        return size;
    }

    public final void write(OutputStream stream) throws IOException
    {
        for (Entry<?, ?> entry : dictionary.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();

            stream.write(key.length());
            stream.write(key.getBytes(ZMQ.CHARSET));

            stream.write(Wire.putUInt32(value.length()));
            stream.write(value.getBytes(ZMQ.CHARSET));
        }
    }

    public final int read(Msg msg, int offset, ParseListener listener)
    {
        return read(msg.buf(), offset, listener);
    }

    public final int read(ByteBuffer msg, int offset, ParseListener listener)
    {
        ByteBuffer data = msg.duplicate();

        data.position(offset);
        int bytesLeft = data.remaining();
        int index = offset;

        while (bytesLeft > 1) {
            final byte nameLength = data.get(index);
            index++;
            bytesLeft -= 1;

            if (bytesLeft < nameLength) {
                break;
            }
            final String name = new String(bytes(data, index, nameLength), ZMQ.CHARSET);
            index += nameLength;
            bytesLeft -= nameLength;

            if (bytesLeft < 4) {
                break;
            }

            final int valueLength = Wire.getUInt32(data, index);
            index += 4;
            bytesLeft -= 4;

            if (bytesLeft < valueLength) {
                break;
            }

            final byte[] value = bytes(data, index, valueLength);
            final String valueAsString = new String(value, ZMQ.CHARSET);
            index += valueLength;
            bytesLeft -= valueLength;
            if (listener != null) {
                int rc = listener.parsed(name, value, valueAsString);
                if (rc != 0) {
                    return rc;
                }
            }
            set(name, valueAsString);
        }
        if (bytesLeft > 0) {
            return ZError.EPROTO;
        }
        return 0;
    }

    private byte[] bytes(final ByteBuffer buf, final int position, final int length)
    {
        final byte[] bytes = new byte[length];
        final int current = buf.position();
        buf.position(position);
        buf.get(bytes, 0, length);
        buf.position(current);
        return bytes;
    }
}
