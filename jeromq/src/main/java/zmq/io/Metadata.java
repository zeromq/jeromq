package zmq.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import zmq.Msg;
import zmq.ZError;
import zmq.ZMQ;
import zmq.util.Wire;

/**
 * The metadata holder class.
 * <p>The class is thread safe, as it uses a {@link ConcurrentHashMap} for the backend</p>
 * <p>Null value are transformed to empty string.</p>
 */
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
    private final Map<String, String> dictionary = new ConcurrentHashMap<>();

    public Metadata()
    {
        super();
    }

    public Metadata(Properties dictionary)
    {
        // Ensure a conversion of each entry to a String.
        // No need to check for nul values as the Properties class ignore null and don't stores them
        dictionary.forEach((key, value) -> this.dictionary.put(key.toString(), value.toString()));
    }

    public Metadata(Map<String, String> dictionary)
    {
        dictionary.forEach((key, value) -> this.dictionary.put(key, Optional.ofNullable(value).orElse("")));
    }

    /**
     * Returns a {@link Set} view of the keys contained in this metadata.
     * The set is backed by the metadata, so changes to the map are
     * reflected in the set, and vice-versa.  If the metadata is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the metadata, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     *
     * @return a set view of the keys contained in this metadata
     */
    public Set<String> keySet()
    {
        return dictionary.keySet();
    }

    /**
     * Returns a {@link Set} view of the properties contained in this metadata.
     * The set is backed by the metadata, so changes to the metadata are
     * reflected in the set, and vice-versa.  If the metadata is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation, or through the
     * {@code setValue} operation on a metadata property returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the metadata, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations.  It does not support the
     * {@code add} or {@code addAll} operations.
     *
     * @return a set view of the properties contained in this metadata
     */
    public Set<Entry<String, String>> entrySet()
    {
        return dictionary.entrySet();
    }

    /**
     * Returns a {@link Collection} view of the values contained in this metadata.
     * The collection is backed by the metadata, so changes to the map are
     * reflected in the collection, and vice-versa.  If the metadata is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * property from the metadata, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     *
     * @return a collection view of the values contained in this map
     */
    public Collection<String> values()
    {
        return dictionary.values();
    }

    /**
     * Removes the property for a name from this metada if it is present.
     *
     * <p>If this map permits null values, then a return value of
     * {@code null} does not <i>necessarily</i> indicate that the map
     * contained no mapping for the key; it's also possible that the map
     * explicitly mapped the key to {@code null}.
     *
     * <p>The map will not contain a mapping for the specified key once the
     * call returns.
     *
     * @param key key whose mapping is to be removed from the map
     */
    public void remove(String key)
    {
        dictionary.remove(key);
    }

    /**
     * Returns the value for this property,
     * or {@code null} if it does not exist.
     *
     * @param property the property name
     * @return the value of the property, or {@code null} if it does not exist.
     */
    public String get(String property)
    {
        return dictionary.get(property);
    }

    /**
     * Define the value for this property. If the value is null, an empty string
     * is stored instead.
     *
     * @param property the property name
     * @param value value to be associated with the specified property
     * @deprecated Use {@link #put(String, String)} instead
     */
    @Deprecated
    public void set(String property, String value)
    {
        put(property, value);
    }

    /**
     * Define the value for this property. If the value is null, an empty string
     * is stored instead.
     *
     * @param property the property name
     * @param value value to be associated with the specified property
     */
    public void put(String property, String value)
    {
        if (value != null) {
            dictionary.put(property, value);
        }
        else {
            dictionary.put(property, "");
        }
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

    public void set(Metadata zapProperties)
    {
        dictionary.putAll(zapProperties.dictionary);
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    public boolean isEmpty()
    {
        return dictionary.isEmpty();
    }

    /**
     * Returns {@code true} if this metada contains the property requested
     *
     * @param property property the name of the property to be tested.
     * @return {@code true} if this metada contains the property
     */
    public boolean containsKey(String property)
    {
        return dictionary.containsKey(property);
    }

    /**
     * Removes all the properties.
     * The map will be empty after this call returns.
     */
    public void clear()
    {
        dictionary.clear();
    }

    /**
     * Returns the number of properties. If it contains more properties
     * than {@code Integer.MAX_VALUE} elements, returns {@code Integer.MAX_VALUE}.
     *
     * @return the number of properties
     */
    public int size()
    {
        return dictionary.size();
    }

    @Override
    public String toString()
    {
        return "Metadata=" + dictionary;
    }

    /**
     * Return the content of the metadata as a new byte array, using the specifications of the ZMTP protocol
     * <pre>
     * property = name value
     * name = OCTET 1*255name-char
     * name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
     * value = 4OCTET *OCTET       ; Size in network byte order
     * </pre>
     * @return a new byte array
     * @throws IllegalStateException if the content can't be serialized
     */
    public byte[] bytes()
    {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream(bytesSize())) {
            write(stream);
            return stream.toByteArray();
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to write content as bytes", e);
        }
    }

    /**
     * Return an approximate size of the serialization of the metadata, it will probably be higher if there is a lot
     * of non ASCII value in it.
     * @return the size estimation
     */
    private int bytesSize()
    {
        int size = 0;
        for (Entry<String, String> entry : dictionary.entrySet()) {
            size += 1;
            size += entry.getKey().length();
            size += 4;
            size += entry.getValue().length();
        }
        return size;
    }

    /**
     * Serialize metadata to an output stream, using the specifications of the ZMTP protocol
     * <pre>
     * property = name value
     * name = OCTET 1*255name-char
     * name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
     * value = 4OCTET *OCTET       ; Size in network byte order
     * </pre>
     * @param stream
     * @throws IOException if an I/O error occurs.
     * @throws IllegalStateException if one of the properties name size is bigger than 255
     */
    public void write(OutputStream stream) throws IOException
    {
        for (Entry<String, String> entry : dictionary.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(ZMQ.CHARSET);
            if (keyBytes.length > 255) {
                throw new IllegalStateException("Trying to serialize an oversize attribute name");
            }
            // write the length as a byte
            stream.write(keyBytes.length);
            stream.write(keyBytes);

            byte[] valueBytes = entry.getValue().getBytes(ZMQ.CHARSET);
            stream.write(Wire.putUInt32(valueBytes.length));
            stream.write(valueBytes);
        }
    }

    /**
     * Deserialize metadata from a {@link Msg}, using the specifications of the ZMTP protocol
     * <pre>
     * property = name value
     * name = OCTET 1*255name-char
     * name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
     * value = 4OCTET *OCTET       ; Size in network byte order
     * </pre>
     * @param msg
     * @param offset
     * @param listener an optional {@link ParseListener}, can be null.
     * @return 0 if successful. Otherwise, it returns {@code zmq.ZError.EPROTO} or the error value from the {@link ParseListener}.
     */
    public int read(Msg msg, int offset, ParseListener listener)
    {
        return read(msg.buf(), offset, listener);
    }

    /**
     * Deserialize metadata from a {@link ByteBuffer}, using the specifications of the ZMTP protocol
     * <pre>
     * property = name value
     * name = OCTET 1*255name-char
     * name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
     * value = 4OCTET *OCTET       ; Size in network byte order
     * </pre>
     * @param msg
     * @param offset
     * @param listener an optional {@link ParseListener}, can be null.
     * @return 0 if successful. Otherwise, it returns {@code zmq.ZError.EPROTO} or the error value from the {@link ParseListener}.
     */
    public int read(ByteBuffer msg, int offset, ParseListener listener)
    {
        ByteBuffer data = msg.duplicate();

        data.position(offset);
        int bytesLeft = data.remaining();
        int index = offset;

        while (bytesLeft > 1) {
            final int nameLength = Byte.toUnsignedInt(data.get(index));
            if (nameLength == 0) {
                break;
            }
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

            if (bytesLeft < valueLength || valueLength < 0) {
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
