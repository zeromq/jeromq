package org.zeromq.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Map.Entry;
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

    public final Set<String> keySet()
    {
        return metadata.keySet();
    }

    public final String get(String key)
    {
        return metadata.get(key);
    }

    public final void set(String key, String value)
    {
        metadata.set(key, value);
    }

    public final byte[] bytes()
    {
        return metadata.bytes();
    }

    public static ZMetadata read(String meta)
    {
        if (meta == null || meta.length() == 0) {
            return null;
        }
        try {
            ByteBuffer buffer = ZMQ.CHARSET.newEncoder().encode(CharBuffer.wrap(meta));
            Metadata data = new Metadata();
            data.read(buffer, 0, null);
            return new ZMetadata(data);
        }
        catch (CharacterCodingException e) {
            e.printStackTrace();
            return null;
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

    public String toString()
    {
        return metadata.toString();
    }

    public int hashCode()
    {
        return metadata.hashCode();
    }

    public boolean equals(Object obj)
    {
        return metadata.equals(obj);
    }
}
