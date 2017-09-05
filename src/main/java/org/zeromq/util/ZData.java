package org.zeromq.util;

import java.io.PrintStream;
import java.util.Arrays;

import org.zeromq.ZMQ;

public class ZData
{
    private static final String HEX_CHAR = "0123456789ABCDEF";

    private final byte[] data;

    public ZData(byte[] data)
    {
        this.data = data;
    }

    /**
     * String equals.
     * Uses String compareTo for the comparison (lexigraphical)
     * @param str String to compare with data
     * @return True if data matches given string
     */
    public boolean streq(String str)
    {
        return streq(data, str);
    }

    /**
     * String equals.
     * Uses String compareTo for the comparison (lexigraphical)
     * @param str String to compare with data
     * @param data the binary data to compare
     * @return True if data matches given string
     */
    public static boolean streq(byte[] data, String str)
    {
        if (data == null) {
            return false;
        }
        return new String(data, ZMQ.CHARSET).compareTo(str) == 0;
    }

    public boolean equals(byte[] that)
    {
        return Arrays.equals(data, that);
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
        if (getClass() != other.getClass()) {
            return false;
        }
        ZData that = (ZData) other;
        if (!Arrays.equals(this.data, that.data)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(data);
    }

    /**
     * Returns a human - readable representation of data
     * @return
     *          A text string or hex-encoded string if data contains any non-printable ASCII characters
     */
    public String toString()
    {
        return toString(data);
    }

    public static String toString(byte[] data)
    {
        if (data == null) {
            return "";
        }
        // Dump message as text or hex-encoded string
        boolean isText = true;
        for (byte aData : data) {
            if (aData < 32 || aData > 127) {
                isText = false;
                break;
            }
        }
        if (isText) {
            return new String(data, ZMQ.CHARSET);
        }
        else {
            return strhex(data);
        }
    }

    /**
     * @return data as a printable hex string
     */
    public String strhex()
    {
        return strhex(data);
    }

    public static String strhex(byte[] data)
    {
        if (data == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (byte aData : data) {
            int b1 = aData >>> 4 & 0xf;
            int b2 = aData & 0xf;
            b.append(HEX_CHAR.charAt(b1));
            b.append(HEX_CHAR.charAt(b2));
        }
        return b.toString();
    }

    public void print(PrintStream out, String prefix)
    {
        print(out, prefix, data, data.length);
    }

    public static void print(PrintStream out, String prefix, byte[] data, int size)
    {
        if (data == null) {
            return;
        }
        if (prefix != null) {
            out.printf("%s", prefix);
        }
        boolean isBin = false;
        int charNbr;
        for (charNbr = 0; charNbr < size; charNbr++) {
            if (data[charNbr] < 9 || data[charNbr] > 127) {
                isBin = true;
            }
        }

        out.printf("[%03d] ", size);
        int maxSize = isBin ? 35 : 70;
        String elipsis = "";
        if (size > maxSize) {
            size = maxSize;
            elipsis = "...";
        }
        for (charNbr = 0; charNbr < size; charNbr++) {
            if (isBin) {
                out.printf("%02X", data[charNbr]);
            }
            else {
                out.printf("%c", data[charNbr]);
            }
        }
        out.printf("%s\n", elipsis);
        out.flush();
    }
}
