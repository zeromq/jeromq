package org.zeromq.proto;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.zeromq.ZFrame;

import zmq.util.Draft;
import zmq.util.Utils;
import zmq.util.Wire;
import zmq.util.function.BiFunction;

/**
 * Needle for de/serialization of data within a frame.
 *
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class ZNeedle
{
    private final ByteBuffer needle; //  Read/write pointer for serialization

    public ZNeedle(ZFrame frame)
    {
        this(frame.getData());
    }

    private ZNeedle(byte[] data)
    {
        needle = ByteBuffer.wrap(data);
    }

    private void checkAvailable(int size)
    {
        Utils.checkArgument(needle.position() + size <= needle.limit(), () -> "Unable to handle " + size + " bytes");
    }

    private void forward(int size)
    {
        needle.position(needle.position() + size);
    }

    private <T> T get(BiFunction<ByteBuffer, Integer, T> getter, int size)
    {
        T value = getter.apply(needle, needle.position());
        forward(size);
        return value;
    }

    //  Put a 1-byte number to the frame
    public void putNumber1(int value)
    {
        checkAvailable(1);
        needle.put((byte) (value & 0xff));
    }

    //  Get a 1-byte number to the frame
    //  then make it unsigned
    public int getNumber1()
    {
        checkAvailable(1);
        int value = needle.get(needle.position()) & 0xff;
        forward(1);
        return value;
    }

    //  Put a 2-byte number to the frame
    public void putNumber2(int value)
    {
        checkAvailable(2);
        Wire.putUInt16(needle, value);
    }

    //  Get a 2-byte number to the frame
    public int getNumber2()
    {
        checkAvailable(2);
        return get(Wire::getUInt16, 2);
    }

    //  Put a 4-byte number to the frame
    public void putNumber4(int value)
    {
        checkAvailable(4);
        Wire.putUInt32(needle, value);
    }

    //  Get a 4-byte number to the frame
    //  then make it unsigned
    public int getNumber4()
    {
        checkAvailable(4);
        return get(Wire::getUInt32, 4);
    }

    //  Put a 8-byte number to the frame
    public void putNumber8(long value)
    {
        checkAvailable(8);
        Wire.putUInt64(needle, value);
    }

    //  Get a 8-byte number to the frame
    public long getNumber8()
    {
        checkAvailable(8);
        return get(Wire::getUInt64, 8);
    }

    //  Put a block to the frame
    public void putBlock(byte[] value, int size)
    {
        needle.put(value, 0, size);
    }

    public byte[] getBlock(int size)
    {
        checkAvailable(size);
        byte[] value = new byte[size];
        needle.get(value);

        return value;
    }

    //  Put a string to the frame
    public void putShortString(String value)
    {
        checkAvailable(value.length() + 1);
        Wire.putShortString(needle, value);
    }

    //  Get a string from the frame
    public String getShortString()
    {
        String value = Wire.getShortString(needle, needle.position());
        forward(value.length() + 1);
        return value;
    }

    public void putLongString(String value)
    {
        checkAvailable(value.length() + 4);
        Wire.putLongString(needle, value);
    }

    //  Get a long string from the frame
    public String getLongString()
    {
        String value = Wire.getLongString(needle, needle.position());
        forward(value.length() + 4);
        return value;
    }

    //  Put a string to the frame
    public void putString(String value)
    {
        if (value.length() > Byte.MAX_VALUE * 2 + 1) {
            putLongString(value);
        }
        else {
            putShortString(value);
        }
    }

    //  Get a short string from the frame
    public String getString()
    {
        return getShortString();
    }

    //  Put a collection of strings to the frame
    public void putList(Collection<String> elements)
    {
        if (elements == null) {
            putNumber1(0);
        }
        else {
            Utils.checkArgument(elements.size() < 256, "Collection has to be smaller than 256 elements");
            putNumber1(elements.size());
            for (String string : elements) {
                putString(string);
            }
        }
    }

    public List<String> getList()
    {
        int size = getNumber1();
        List<String> list = new ArrayList<>(size);
        for (int idx = 0; idx < size; ++ idx) {
            list.add(getString());
        }
        return list;
    }

    //  Put a map of strings to the frame
    public void putMap(Map<String, String> map)
    {
        if (map == null) {
            putNumber1(0);
        }
        else {
            Utils.checkArgument(map.size() < 256, "Map has to be smaller than 256 elements");
            putNumber1(map.size());
            for (Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey().contains("=")) {
                    throw new IllegalArgumentException("Keys cannot contain '=' sign. " + entry);
                }
                if (entry.getValue().contains("=")) {
                    throw new IllegalArgumentException("Values cannot contain '=' sign. " + entry);
                }
                String val = entry.getKey() + "=" + entry.getValue();
                putString(val);
            }
        }
    }

    public Map<String, String> getMap()
    {
        int size = getNumber1();
        Map<String, String> map = new HashMap<>(size);
        for (int idx = 0; idx < size; ++idx) {
            String[] kv = getString().split("=");
            assert (kv.length == 2);
            map.put(kv[0], kv[1]);
        }
        return map;
    }

    @Override
    public String toString()
    {
        return "ZNeedle [position=" + needle.position() + ", ceiling=" + needle.limit() + "]";
    }
}
