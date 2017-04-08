package zmq.util;

import java.nio.ByteBuffer;

//  Z85 codec, taken from 0MQ RFC project, implements RFC32 Z85 encoding
public class Z85
{
    private Z85()
    {
    }

    //  Maps base 256 to base 85
    private static final String encoder = "0123456789" + "abcdefghij" + "klmnopqrst" + "uvwxyzABCD" + "EFGHIJKLMN"
            + "OPQRSTUVWX" + "YZ.-:+=^!/" + "*?&<>()[]{" + "}@%$#";

    //  Maps base 85 to base 256
    //  We chop off lower 32 and higher 128 ranges
    private static final byte[] decoder = { 0x00, 0x44, 0x00, 0x54, 0x53, 0x52, 0x48, 0x00, 0x4B, 0x4C, 0x46, 0x41,
            0x00, 0x3F, 0x3E, 0x45, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x40, 0x00, 0x49, 0x42,
            0x4A, 0x47, 0x51, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32,
            0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x4D, 0x00, 0x4E, 0x43, 0x00, 0x00, 0x0A,
            0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C,
            0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x4F, 0x00, 0x50, 0x00, 0x00 };

    //  --------------------------------------------------------------------------
    //  Encode a binary frame as a string; destination string MUST be at least
    //  size * 5 / 4 bytes long plus 1 byte for the null terminator. Returns
    //  dest. Size must be a multiple of 4.
    //  Returns NULL and sets errno = EINVAL for invalid input.

    public static String encode(byte[] data, int size)
    {
        if (size % 4 != 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int byteNbr = 0;
        long value = 0;
        while (byteNbr < size) {
            //  Accumulate value in base 256 (binary)
            int d = data[byteNbr++] & 0xff;
            if (d < 0) {
                System.out.print("");
            }
            value = value * 256 + d;
            if (byteNbr % 4 == 0) {
                //  Output value in base 85
                int divisor = 85 * 85 * 85 * 85;
                while (divisor != 0) {
                    int index = (int) (value / divisor % 85);
                    if (index < 0) {
                        System.out.print("");
                    }
                    builder.append(encoder.charAt(index));
                    divisor /= 85;
                }
                value = 0;
            }
        }
        assert (builder.length() == size * 5 / 4);
        return builder.toString();
    }

    //  --------------------------------------------------------------------------
    //  Decode an encoded string into a binary frame; dest must be at least
    //  strlen (string) * 4 / 5 bytes long. Returns dest. strlen (string)
    //  must be a multiple of 5.
    //  Returns NULL and sets errno = EINVAL for invalid input.

    public static byte[] decode(String string)
    {
        if (string.length() % 5 != 0) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(string.length() * 4 / 5);

        int byteNbr = 0;
        int charNbr = 0;
        int stringLen = string.length();
        long value = 0;
        while (charNbr < stringLen) {
            //  Accumulate value in base 85
            value = value * 85 + (decoder[string.charAt(charNbr++) - 32] & 0xff);
            if (charNbr % 5 == 0) {
                //  Output value in base 256
                int divisor = 256 * 256 * 256;
                while (divisor != 0) {
                    buf.put(byteNbr++, (byte) ((value / divisor) % 256));
                    divisor /= 256;
                }
                value = 0;
            }
        }
        assert (byteNbr == string.length() * 4 / 5);
        return buf.array();
    }
}
