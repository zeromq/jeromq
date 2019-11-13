package org.zeromq.proto;

import java.util.regex.Pattern;

import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import org.zeromq.ZMsg;

import zmq.ZError;
import zmq.util.Draft;

/**
 * De/serialization of data within a message.
 *
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public class ZPicture
{
    private static final Pattern FORMAT        = Pattern.compile("[i1248sbcfz]*m?");
    private static final Pattern BINARY_FORMAT = Pattern.compile("[1248sSbcf]*m?");

    /**
     * Creates a binary encoded 'picture' message to the socket (or actor), so it can be sent.
     * The arguments are encoded in a binary format that is compatible with zproto, and
     * is designed to reduce memory allocations.
     *
     * @param picture The picture argument is a string that defines the
     *                type of each argument. Supports these argument types:
     *                <table>
     *                <caption> </caption>
     *                <tr><th style="text-align:left">pattern</th><th style="text-align:left">java type</th><th style="text-align:left">zproto type</th></tr>
     *                <tr><td>1</td><td>int</td><td>type = "number" size = "1"</td></tr>
     *                <tr><td>2</td><td>int</td><td>type = "number" size = "2"</td></tr>
     *                <tr><td>4</td><td>long</td><td>type = "number" size = "3"</td></tr>
     *                <tr><td>8</td><td>long</td><td>type = "number" size = "4"</td></tr>
     *                <tr><td>s</td><td>String, 0-255 chars</td><td>type = "string"</td></tr>
     *                <tr><td>S</td><td>String, 0-2^32-1 chars</td><td>type = "longstr"</td></tr>
     *                <tr><td>b</td><td>byte[], 0-2^32-1 bytes</td><td>type = "chunk"</td></tr>
     *                <tr><td>c</td><td>byte[], 0-2^32-1 bytes</td><td>type = "chunk"</td></tr>
     *                <tr><td>f</td><td>ZFrame</td><td>type = "frame"</td></tr>
     *                <tr><td>m</td><td>ZMsg</td><td>type = "msg" <b>Has to be the last element of the picture</b></td></tr>
     *                </table>
     * @param args    Arguments according to the picture
     * @return true when it has been queued on the socket and ØMQ has assumed responsibility for the message.
     * This does not indicate that the message has been transmitted to the network.
     * @apiNote Does not change or take ownership of any arguments.
     */
    @Draft
    public ZMsg msgBinaryPicture(String picture, Object... args)
    {
        if (!BINARY_FORMAT.matcher(picture).matches()) {
            throw new ZMQException(picture + " is not in expected binary format " + BINARY_FORMAT.pattern(),
                    ZError.EPROTO);
        }
        ZMsg msg = new ZMsg();

        //  Pass 1: calculate total size of data frame
        int frameSize = 0;
        for (int index = 0; index < picture.length(); index++) {
            char pattern = picture.charAt(index);
            switch (pattern) {
            case '1': {
                frameSize += 1;
                break;
            }
            case '2': {
                frameSize += 2;
                break;
            }
            case '4': {
                frameSize += 4;
                break;
            }
            case '8': {
                frameSize += 8;
                break;
            }
            case 's': {
                String string = (String) args[index];
                frameSize += 1 + (string != null ? string.getBytes(ZMQ.CHARSET).length : 0);
                break;
            }
            case 'S': {
                String string = (String) args[index];
                frameSize += 4 + (string != null ? string.getBytes(ZMQ.CHARSET).length : 0);
                break;
            }
            case 'b':
            case 'c': {
                byte[] block = (byte[]) args[index];
                frameSize += 4 + block.length;
                break;
            }
            case 'f': {
                ZFrame frame = (ZFrame) args[index];
                msg.add(frame);
                break;
            }
            case 'm': {
                ZMsg other = (ZMsg) args[index];
                if (other == null) {
                    msg.add(new ZFrame((byte[]) null));
                }
                else {
                    msg.addAll(other);
                }
                break;
            }
            default:
                assert (false) : "invalid picture element '" + pattern + "'";
            }
        }

        //  Pass 2: encode data into data frame
        ZFrame frame = new ZFrame(new byte[frameSize]);
        ZNeedle needle = new ZNeedle(frame);
        for (int index = 0; index < picture.length(); index++) {
            char pattern = picture.charAt(index);
            switch (pattern) {
            case '1': {
                needle.putNumber1((int) args[index]);
                break;
            }
            case '2': {
                needle.putNumber2((int) args[index]);
                break;
            }
            case '4': {
                needle.putNumber4((int) args[index]);
                break;
            }
            case '8': {
                needle.putNumber8((long) args[index]);
                break;
            }
            case 's': {
                needle.putString((String) args[index]);
                break;
            }
            case 'S': {
                needle.putLongString((String) args[index]);
                break;
            }
            case 'b':
            case 'c': {
                byte[] block = (byte[]) args[index];
                needle.putNumber4(block.length);
                needle.putBlock(block, block.length);
                break;
            }
            case 'f':
            case 'm':
                break;
            default:
                assert (false) : "invalid picture element '" + pattern + "'";
            }
        }
        msg.addFirst(frame);
        return msg;
    }

    @Draft
    public boolean sendBinaryPicture(Socket socket, String picture, Object... args)
    {
        return msgBinaryPicture(picture, args).send(socket);
    }

    /**
     * Receive a binary encoded 'picture' message from the socket (or actor).
     * This method is similar to {@link org.zeromq.ZMQ.Socket#recv()}, except the arguments are encoded
     * in a binary format that is compatible with zproto, and is designed to
     * reduce memory allocations.
     *
     * @param picture The picture argument is a string that defines
     *                the type of each argument. See {@link #sendBinaryPicture(Socket, String, Object...)}
     *                for the supported argument types.
     * @return the picture elements as object array
     **/
    @Draft
    public Object[] recvBinaryPicture(Socket socket, final String picture)
    {
        if (!BINARY_FORMAT.matcher(picture).matches()) {
            throw new ZMQException(picture + " is not in expected binary format " + BINARY_FORMAT.pattern(),
                    ZError.EPROTO);
        }
        ZFrame frame = ZFrame.recvFrame(socket);
        if (frame == null) {
            return null;
        }
        //  Get the data frame
        ZNeedle needle = new ZNeedle(frame);

        Object[] results = new Object[picture.length()];
        for (int index = 0; index < picture.length(); index++) {
            char pattern = picture.charAt(index);
            switch (pattern) {
            case '1': {
                results[index] = needle.getNumber1();
                break;
            }
            case '2': {
                results[index] = needle.getNumber2();
                break;
            }
            case '4': {
                results[index] = needle.getNumber4();
                break;
            }
            case '8': {
                results[index] = needle.getNumber8();
                break;
            }
            case 's': {
                results[index] = needle.getString();
                break;
            }
            case 'S': {
                results[index] = needle.getLongString();
                break;
            }
            case 'b':
            case 'c': {
                int size = needle.getNumber4();
                results[index] = needle.getBlock(size);
                break;
            }
            case 'f': {
                // Get next frame off socket
                results[index] = ZFrame.recvFrame(socket);
                break;
            }
            case 'm': {
                // Get zero or more remaining frames
                results[index] = ZMsg.recvMsg(socket);
                break;
            }
            default:
                assert (false) : "invalid picture element '" + pattern + "'";
            }
        }
        return results;
    }

    /**
     * Queues a 'picture' message to the socket (or actor), so it can be sent.
     *
     * @param picture The picture is a string that defines the type of each frame.
     *                This makes it easy to send a complex multiframe message in
     *                one call. The picture can contain any of these characters,
     *                each corresponding to zero or one arguments:
     *
     *                <table>
     *                <caption> </caption>
     *                <tr><td>i = int  (stores signed integer)</td></tr>
     *                <tr><td>1 = byte (stores 8-bit unsigned integer)</td></tr>
     *                <tr><td>2 = int  (stores 16-bit unsigned integer)</td></tr>
     *                <tr><td>4 = long (stores 32-bit unsigned integer)</td></tr>
     *                <tr><td>8 = long (stores 64-bit unsigned integer)</td></tr>
     *                <tr><td>s = String</td></tr>
     *                <tr><td>b = byte[]</td></tr>
     *                <tr><td>c = byte[]</td></tr>
     *                <tr><td>f = ZFrame</td></tr>
     *                <tr><td>m = ZMsg (sends all frames in the ZMsg)<b>Has to be the last element of the picture</b></td></tr>
     *                <tr><td>z = sends zero-sized frame (0 arguments)</td></tr>
     *                </table>
     *                Note that s, b, f and m are encoded the same way and the choice is
     *                offered as a convenience to the sender, which may or may not already
     *                have data in a ZFrame or ZMsg. Does not change or take ownership of
     *                any arguments.
     *
     *                Also see {@link #recvPicture(Socket, String)}} how to recv a
     *                multiframe picture.
     * @param args    Arguments according to the picture
     * @return true if successful, false if sending failed for any reason
     */
    @Draft
    public boolean sendPicture(Socket socket, String picture, Object... args)
    {
        if (!FORMAT.matcher(picture).matches()) {
            throw new ZMQException(picture + " is not in expected format " + FORMAT.pattern(), ZError.EPROTO);
        }
        ZMsg msg = new ZMsg();
        for (int pictureIndex = 0, argIndex = 0; pictureIndex < picture.length(); pictureIndex++, argIndex++) {
            char pattern = picture.charAt(pictureIndex);
            switch (pattern) {
            case 'i': {
                msg.add(String.format("%d", (int) args[argIndex]));
                break;
            }
            case '1': {
                msg.add(String.format("%d", (0xff) & (int) args[argIndex]));
                break;
            }
            case '2': {
                msg.add(String.format("%d", (0xffff) & (int) args[argIndex]));
                break;
            }
            case '4': {
                msg.add(String.format("%d", (0xffffffff) & (int) args[argIndex]));
                break;
            }
            case '8': {
                msg.add(String.format("%d", (long) args[argIndex]));
                break;
            }
            case 's': {
                msg.add((String) args[argIndex]);
                break;
            }
            case 'b':
            case 'c': {
                msg.add((byte[]) args[argIndex]);
                break;
            }
            case 'f': {
                msg.add((ZFrame) args[argIndex]);
                break;
            }
            case 'm': {
                ZMsg msgParm = (ZMsg) args[argIndex];
                while (msgParm.size() > 0) {
                    msg.add(msgParm.pop());
                }
                break;
            }
            case 'z': {
                msg.add((byte[]) null);
                argIndex--;
                break;
            }
            default:
                assert (false) : "invalid picture element '" + pattern + "'";
            }
        }
        return msg.send(socket, false);
    }

    /**
     * Receive a 'picture' message to the socket (or actor).
     *
     *
     * @param picture The picture is a string that defines the type of each frame.
     *                This makes it easy to recv a complex multiframe message in
     *                one call. The picture can contain any of these characters,
     *                each corresponding to zero or one elements in the result:
     *
     *                <table>
     *               <caption> </caption>
     *                <tr><td>i = int (stores signed integer)</td></tr>
     *                <tr><td>1 = int (stores 8-bit unsigned integer)</td></tr>
     *                <tr><td>2 = int (stores 16-bit unsigned integer)</td></tr>
     *                <tr><td>4 = long (stores 32-bit unsigned integer)</td></tr>
     *                <tr><td>8 = long (stores 64-bit unsigned integer)</td></tr>
     *                <tr><td>s = String</td></tr>
     *                <tr><td>b = byte[]</td></tr>
     *                <tr><td>f = ZFrame (creates zframe)</td></tr>
     *                <tr><td>m = ZMsg (creates a zmsg with the remaing frames)</td></tr>
     *                <tr><td>z = null, asserts empty frame (0 arguments)</td></tr>
     *                </table>
     *
     *                Also see {@link #sendPicture(Socket, String, Object...)} how to send a
     *                multiframe picture.
     *
     * @return the picture elements as object array
     */
    @Draft
    public Object[] recvPicture(Socket socket, String picture)
    {
        if (!FORMAT.matcher(picture).matches()) {
            throw new ZMQException(picture + " is not in expected format " + FORMAT.pattern(), ZError.EPROTO);
        }
        Object[] elements = new Object[picture.length()];
        for (int index = 0; index < picture.length(); index++) {
            char pattern = picture.charAt(index);
            switch (pattern) {
            case 'i': {
                elements[index] = Integer.valueOf(socket.recvStr());
                break;
            }
            case '1': {
                elements[index] = (0xff) & Integer.valueOf(socket.recvStr());
                break;
            }
            case '2': {
                elements[index] = (0xffff) & Integer.valueOf(socket.recvStr());
                break;
            }
            case '4': {
                elements[index] = (0xffffffff) & Integer.valueOf(socket.recvStr());
                break;
            }
            case '8': {
                elements[index] = Long.valueOf(socket.recvStr());
                break;
            }
            case 's': {
                elements[index] = socket.recvStr();
                break;
            }
            case 'b':
            case 'c': {
                elements[index] = socket.recv();
                break;
            }
            case 'f': {
                elements[index] = ZFrame.recvFrame(socket);

                break;
            }
            case 'm': {
                elements[index] = ZMsg.recvMsg(socket);
                break;
            }
            case 'z': {
                ZFrame zeroFrame = ZFrame.recvFrame(socket);
                if (zeroFrame == null || zeroFrame.size() > 0) {
                    throw new ZMQException("zero frame is not empty", ZError.EPROTO);
                }
                elements[index] = new ZFrame((byte[]) null);
                break;
            }
            default:
                assert (false) : "invalid picture element '" + pattern + "'";
            }
        }
        return elements;
    }
}
