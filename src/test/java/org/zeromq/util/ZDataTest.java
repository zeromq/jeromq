package org.zeromq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.zeromq.ZMQ;

public class ZDataTest
{
    @Test
    public void testPrint()
    {
        byte[] buf = new byte[10];
        Arrays.fill(buf, (byte) 0xAA);
        ZData data = new ZData(buf);

        data.print(System.out, "ZData: ");
    }

    @Test
    public void testPrintNonPrintable()
    {
        byte[] buf = new byte[12];
        Arrays.fill(buf, (byte) 0x04);
        ZData data = new ZData(buf);

        data.print(System.out, "ZData: ");
    }

    @Test
    public void testToString()
    {
        ZData data = new ZData("test".getBytes(ZMQ.CHARSET));

        String string = data.toString();
        assertThat(string, is("test"));
    }

    @Test
    public void testToStringNonPrintable()
    {
        byte[] buf = new byte[2];
        Arrays.fill(buf, (byte) 0x04);
        ZData data = new ZData(buf);

        String string = data.toString();
        assertThat(string, is("0404"));
    }

    @Test
    public void testStreq()
    {
        ZData data = new ZData("test".getBytes(ZMQ.CHARSET));

        assertThat(data.streq("test"), is(true));
    }

    @Test
    public void testEquals()
    {
        ZData data = new ZData("test".getBytes(ZMQ.CHARSET));
        ZData other = new ZData("test".getBytes(ZMQ.CHARSET));

        assertThat(data.equals(other), is(true));
    }
}
