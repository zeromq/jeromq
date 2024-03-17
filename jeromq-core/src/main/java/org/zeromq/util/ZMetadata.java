package org.zeromq.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.zeromq.ZConfig;
import org.zeromq.ZMQ;

import zmq.io.Metadata;

public class ZMetadata
{
    private final Metadata metadata;

    public ZMetadata()
    {
        this(new Metadata());
    }

    public ZMetadata(Metadata metadata)
    {
        this.metadata = metadata;
    }

    public Set<String> keySet()
    {
        return metadata.keySet();
    }

    public String get(String key)
    {
        return metadata.get(key);
    }

    public void set(String key, String value)
    {
        metadata.put(key, value);
    }

    public void remove(String key)
    {
        metadata.remove(key);
    }

    public byte[] bytes()
    {
        return metadata.bytes();
    }

    public String toString()
    {
        return metadata.toString();
    }

    public int hashCode()
    {
        return metadata.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ZMetadata zMetadata = (ZMetadata) o;
        return Objects.equals(metadata, zMetadata.metadata);
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
        return metadata.entrySet();
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
        return metadata.values();
    }

    public void set(Metadata zapProperties)
    {
        metadata.set(zapProperties);
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    public boolean isEmpty()
    {
        return metadata.isEmpty();
    }

    /**
     * Returns {@code true} if this metada contains the property requested
     *
     * @param property property the name of the property to be tested.
     * @return {@code true} if this metada contains the property
     */
    public boolean containsKey(String property)
    {
        return metadata.containsKey(property);
    }

    /**
     * Removes all the properties.
     * The map will be empty after this call returns.
     */
    public void clear()
    {
        metadata.clear();
    }

    /**
     * Returns the number of properties. If it contains more properties
     * than {@code Integer.MAX_VALUE} elements, returns {@code Integer.MAX_VALUE}.
     *
     * @return the number of properties
     */
    public int size()
    {
        return metadata.size();
    }

    /**
     * Serialize metadata to an output stream, using the specifications of the ZMTP protocol
     * <pre>
     * property = name value
     * name = OCTET 1*255name-char
     * name-char = ALPHA | DIGIT | "-" | "_" | "." | "+"
     * value = 4OCTET *OCTET       ; Size in network byte order
     * </pre>
     *
     * @param stream the output stream
     * @throws IOException           if an I/O error occurs.
     * @throws IllegalStateException if one of the properties name size is bigger than 255
     */
    public void write(OutputStream stream) throws IOException
    {
        metadata.write(stream);
    }

    public static ZMetadata read(String meta)
    {
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        try {
            ByteBuffer buffer = ZMQ.CHARSET.newEncoder().encode(CharBuffer.wrap(meta));
            Metadata data = new Metadata();
            data.read(buffer, 0, null);
            return new ZMetadata(data);
        }
        catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Not a parsable metadata string");
        }
    }

    public static ZMetadata read(ZConfig conf)
    {
        ZConfig meta = conf.getChild("metadata");
        if (meta == null) {
            return null;
        }
        ZMetadata metadata = new ZMetadata();
        for (Entry<String, String> entry : meta.getValues().entrySet()) {
            metadata.set(entry.getKey(), entry.getValue());
        }
        return metadata;
    }
}
