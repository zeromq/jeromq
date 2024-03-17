package org.zeromq.proto;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;

public class ZNeedleTest
{
    @Test
    public void testGetByte()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);
        assertThat(needle.getNumber1(), is(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIncorrectByte()
    {
        ZFrame frame = new ZFrame(new byte[0]);
        ZNeedle needle = new ZNeedle(frame);
        needle.getNumber1();
    }

    @Test
    public void testGetShort()
    {
        ZFrame frame = new ZFrame(new byte[2]);
        ZNeedle needle = new ZNeedle(frame);
        assertThat(needle.getNumber2(), is(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIncorrectShort()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);
        needle.getNumber2();
    }

    @Test
    public void testGetInt()
    {
        ZFrame frame = new ZFrame(new byte[4]);
        ZNeedle needle = new ZNeedle(frame);
        assertThat(needle.getNumber4(), is(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIncorrectInt()
    {
        ZFrame frame = new ZFrame(new byte[3]);
        ZNeedle needle = new ZNeedle(frame);
        needle.getNumber4();
    }

    @Test
    public void testGetLong()
    {
        ZFrame frame = new ZFrame(new byte[8]);
        ZNeedle needle = new ZNeedle(frame);
        assertThat(needle.getNumber8(), is(0L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIncorrectLong()
    {
        ZFrame frame = new ZFrame(new byte[7]);
        ZNeedle needle = new ZNeedle(frame);
        needle.getNumber8();
    }

    @Test
    public void testSimpleValues()
    {
        ZFrame frame = new ZFrame(new byte[15 + 26 + 1 + 4 + 8 + 2]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putNumber1(1);
        needle.putNumber2(124);
        needle.putNumber4(3245678);
        needle.putNumber8(87654225);
        byte[] block = new byte[10];
        Arrays.fill(block, (byte) 2);
        needle.putBlock(block, 8);
        needle.putShortString("abcdefg");
        needle.putLongString("hijklmnopqrstuvwxyz");
        needle.putNumber2(421);

        needle = new ZNeedle(frame);
        assertThat(needle.getNumber1(), is(1));
        assertThat(needle.getNumber2(), is(124));
        assertThat(needle.getNumber4(), is(3245678));
        assertThat(needle.getNumber8(), is(87654225L));
        block = new byte[8];
        Arrays.fill(block, (byte) 2);
        assertThat(needle.getBlock(8), is(block));
        assertThat(needle.getShortString(), is("abcdefg"));
        assertThat(needle.getLongString(), is("hijklmnopqrstuvwxyz"));
        assertThat(needle.getNumber2(), is(421));

        String s = needle.toString();
        assertThat(s, is("ZNeedle [position=56, ceiling=56]"));
    }

    @Test
    public void testLongString()
    {
        ZFrame frame = new ZFrame(new byte[300]);
        ZNeedle needle = new ZNeedle(frame);

        byte[] bytes = new byte[294];
        Arrays.fill(bytes, (byte) 'A');
        String string = new String(bytes, ZMQ.CHARSET);
        needle.putString(string);
        needle.putNumber2(42);

        needle = new ZNeedle(frame);
        assertThat(needle.getLongString(), is(string));
        assertThat(needle.getNumber2(), is(42));
    }

    @Test
    public void testList()
    {
        ZFrame frame = new ZFrame(new byte[26]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putList(Arrays.asList("1", "2", "34", "567"));
        needle.putList(Arrays.asList("864", "43", "9", "0"));

        needle = new ZNeedle(frame);
        assertThat(needle.getList(), is(Arrays.asList("1", "2", "34", "567")));
        assertThat(needle.getList(), is(Arrays.asList("864", "43", "9", "0")));
    }

    @Test
    public void testNullList()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putList(null);

        needle = new ZNeedle(frame);
        assertThat(needle.getList(), is(Collections.emptyList()));
    }

    @Test
    public void testEmptyList()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putList(Collections.emptyList());

        needle = new ZNeedle(frame);
        assertThat(needle.getList(), is(Collections.emptyList()));
    }

    @Test
    public void testMap()
    {
        ZFrame frame = new ZFrame(new byte[2 * (1 + 10 + 4 + 7)]);
        ZNeedle needle = new ZNeedle(frame);

        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        map.put("1", "2");
        map.put("34", "567");
        needle.putMap(map);
        needle.putMap(map);

        needle = new ZNeedle(frame);
        assertThat(needle.getMap(), is(map));
        assertThat(new HashSet<>(needle.getList()), is(new HashSet<>(Arrays.asList("key=value", "1=2", "34=567"))));
    }

    @Test
    public void testNullMap()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putMap(null);

        needle = new ZNeedle(frame);
        assertThat(needle.getMap(), is(Collections.emptyMap()));
    }

    @Test
    public void testEmptyMap()
    {
        ZFrame frame = new ZFrame(new byte[1]);
        ZNeedle needle = new ZNeedle(frame);

        needle.putMap(Collections.emptyMap());

        needle = new ZNeedle(frame);
        assertThat(needle.getMap(), is(Collections.emptyMap()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMapIncorrectKey()
    {
        ZFrame frame = new ZFrame(new byte[(1 + 10)]);
        ZNeedle needle = new ZNeedle(frame);

        Map<String, String> map = new HashMap<>();
        map.put("ke=", "value");
        needle.putMap(map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMapIncorrectValue()
    {
        ZFrame frame = new ZFrame(new byte[(1 + 10)]);
        ZNeedle needle = new ZNeedle(frame);

        Map<String, String> map = new HashMap<>();
        map.put("key", "=alue");
        needle.putMap(map);
    }
}
