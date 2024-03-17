package org.zeromq.proto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import org.zeromq.ZMsg;

public class ZPictureTest
{
    private final ZPicture pic = new ZPicture();

    @Test(expected = ZMQException.class)
    public void testInvalidBinaryPictureFormat()
    {
        String picture = "a";
        pic.msgBinaryPicture(picture, 255);
    }

    @Test(expected = ZMQException.class)
    public void testSendInvalidPictureFormat()
    {
        String picture = " ";
        pic.sendPicture(null, picture, 255);
    }

    @Test(expected = ZMQException.class)
    public void testReceiveInvalidPictureFormat()
    {
        String picture = "x";
        pic.recvPicture(null, picture);
    }

    @Test(expected = ZMQException.class)
    public void testInvalidPictureMsgNotInTheEnd()
    {
        String picture = "m1";

        ZMsg msg = new ZMsg().push("Hello");
        pic.msgBinaryPicture(picture, msg, 255);
    }

    @Test(expected = ZMQException.class)
    public void testReceiveInvalidPictureMsgNotInTheEnd()
    {
        String picture = "m1";
        pic.recvBinaryPicture(null, picture);
    }

    @Test
    public void testValidPictureNullMsgInTheEnd()
    {
        String picture = "fm";

        ZMsg msg = pic.msgBinaryPicture(picture, new ZFrame("My frame"), null);
        assertThat(msg.getLast().size(), is(0));
    }

    @Test
    public void testSocketSendRecvPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "i1248sbcfzm";

        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = pic.sendPicture(
                             push,
                             picture,
                             -456,
                             255,
                             65535,
                             429496729,
                             Long.MAX_VALUE,
                             "Hello World",
                             "ABC".getBytes(ZMQ.CHARSET),
                             "DEF".getBytes(ZMQ.CHARSET),
                             new ZFrame("My frame"),
                             msg);
        assertThat(rc, is(true));

        Object[] objects = pic.recvPicture(pull, picture);
        assertThat(objects[0], is(-456));
        assertThat(objects[1], is(255));
        assertThat(objects[2], is(65535));
        assertThat(objects[3], is(429496729));
        assertThat(objects[4], is(Long.MAX_VALUE));
        assertThat(objects[5], is("Hello World"));
        assertThat(objects[6], is("ABC".getBytes(zmq.ZMQ.CHARSET)));
        assertThat(objects[7], is("DEF".getBytes(zmq.ZMQ.CHARSET)));
        assertThat(objects[8], is(equalTo(new ZFrame("My frame"))));
        assertThat(objects[9], is(equalTo(new ZFrame((byte[]) null))));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[10], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvBinaryPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "1248sSbcfm";

        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = pic.sendBinaryPicture(
                                   push,
                                   picture,
                                   255,
                                   65535,
                                   429496729,
                                   Long.MAX_VALUE,
                                   "Hello World",
                                   "Hello cruel World!",
                                   "ABC".getBytes(ZMQ.CHARSET),
                                   "DEF".getBytes(ZMQ.CHARSET),
                                   new ZFrame("My frame"),
                                   msg);
        assertThat(rc, is(true));

        Object[] objects = pic.recvBinaryPicture(pull, picture);
        assertThat(objects[0], is(255));
        assertThat(objects[1], is(65535));
        assertThat(objects[2], is(429496729));
        assertThat(objects[3], is(Long.MAX_VALUE));
        assertThat(objects[4], is("Hello World"));
        assertThat(objects[5], is("Hello cruel World!"));
        assertThat(objects[6], is("ABC".getBytes(zmq.ZMQ.CHARSET)));
        assertThat(objects[7], is("DEF".getBytes(zmq.ZMQ.CHARSET)));
        assertThat(objects[8], is(equalTo(new ZFrame("My frame"))));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[9], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }
}
