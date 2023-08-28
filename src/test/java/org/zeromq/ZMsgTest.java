package org.zeromq;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

import zmq.ZError;

/**
 * Created by hartmann on 3/21/14.
 */
public class ZMsgTest
{
    @Test
    public void testRecvFrame()
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket socket = ctx.socket(SocketType.PULL);

        ZFrame frame = ZFrame.recvFrame(socket, ZMQ.NOBLOCK);
        assertThat(frame, nullValue());

        socket.close();
        ctx.close();
    }

    @Test
    public void testRecvMsg()
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket socket = ctx.socket(SocketType.PULL);

        ZMsg.recvMsg(socket, ZMQ.NOBLOCK, (msg)-> assertThat(msg, nullValue()));

        socket.close();
        ctx.close();
    }

    @Test
    public void testRecvNullByteMsg()
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket sender = ctx.socket(SocketType.PUSH);
        ZMQ.Socket receiver = ctx.socket(SocketType.PULL);

        receiver.bind("inproc://" + this.hashCode());
        sender.connect("inproc://" + this.hashCode());

        sender.send(new byte[0]);
        ZMsg msg = ZMsg.recvMsg(receiver, ZMQ.NOBLOCK);
        assertThat(msg, notNullValue());

        sender.close();
        receiver.close();
        ctx.close();
    }

    @Test
    public void testContentSize()
    {
        ZMsg msg = new ZMsg();

        msg.add(new byte[0]);
        assertThat(msg.contentSize(), is(0L));

        msg.add(new byte[1]);
        assertThat(msg.contentSize(), is(1L));
    }

    @Test
    public void testEquals()
    {
        ZMsg msg = new ZMsg().addString("123");
        ZMsg other = new ZMsg();

        assertThat(msg.equals(msg), is(true));
        assertThat(msg.equals(null), is(false));
        assertThat(msg.equals(""), is(false));

        assertThat(msg.equals(other), is(false));

        other.add("345");
        assertThat(msg.equals(other), is(false));
        other.popString();
        other.add("123");
        assertThat(msg.equals(other), is(true));

        assertThat(msg.duplicate(), is(msg));

        msg.destroy();
    }

    @Test
    public void testHashcode()
    {
        ZMsg msg = new ZMsg();
        assertThat(msg.hashCode(), is(0));

        msg.add("123");

        ZMsg other = ZMsg.newStringMsg("123");

        assertThat(msg.hashCode(), is(other.hashCode()));

        other = new ZMsg().append("2");

        assertThat(msg.hashCode(), is(not(equalTo(other.hashCode()))));
    }

    @Test
    public void testDump()
    {
        ZMsg msg = new ZMsg().append(new byte[0]).append(new byte[] { (byte) 0xAA });
        msg.dump();

        StringBuilder out = new StringBuilder();
        msg.dump(out);

        System.out.println(msg);

        assertThat(out.toString(), endsWith("[000] \n[001] AA\n"));
    }

    @Test
    public void testWrapUnwrap()
    {
        ZMsg msg = new ZMsg().wrap(new ZFrame("456"));
        assertThat(msg.size(), is(2));
        ZFrame frame = msg.unwrap();
        assertThat(frame.toString(), is("456"));
        assertThat(msg.size(), is(0));
    }

    @Test
    public void testSaveLoad()
    {
        ZMsg msg = new ZMsg().append("123");

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);

        ZMsg.save(msg, out);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(stream.toByteArray()));

        ZMsg loaded = ZMsg.load(in);

        assertThat(msg, is(loaded));
    }

    @Test
    public void testAppend()
    {
        ZMsg msg = new ZMsg().append((ZMsg) null).append(ZMsg.newStringMsg("123"));
        assertThat(msg.popString(), is("123"));

        msg.append(ZMsg.newStringMsg("123")).append(msg);
        assertThat(msg.size(), is(2));
        assertThat(msg.contentSize(), is(6L));
    }

    @Test
    public void testPop()
    {
        ZMsg msg = new ZMsg();
        assertThat(msg.popString(), nullValue());
    }

    @Test
    public void testRemoves()
    {
        ZMsg msg = ZMsg.newStringMsg("1", "2", "3", "4");
        assertThat(msg.remove(), is(new ZFrame("1")));
        assertThat(msg.removeLast(), is(new ZFrame("4")));
        assertThat(msg.removeFirst(), is(new ZFrame("2")));

        msg.add(new ZFrame("5"));
        msg.add(new ZFrame("6"));
        msg.add(new ZFrame("7"));
        assertThat(msg.size(), is(4));

        boolean removed = msg.removeFirstOccurrence(new ZFrame("5"));
        assertThat(removed, is(true));

        removed = msg.removeFirstOccurrence(new ZFrame("5"));
        assertThat(removed, is(false));

        removed = msg.removeFirstOccurrence(new ZFrame("7"));
        assertThat(removed, is(true));

        assertThat(msg.size(), is(2));

        msg.add(new ZFrame("6"));
        msg.add(new ZFrame("4"));
        msg.add(new ZFrame("6"));

        removed = msg.removeLastOccurrence(new ZFrame("6"));
        assertThat(removed, is(true));
        assertThat(msg.size(), is(4));
    }

    @Test
    public void testMessageEquals()
    {
        ZMsg msg = new ZMsg();
        ZFrame hello = new ZFrame("Hello");
        ZFrame world = new ZFrame("World");
        msg.add(hello);
        msg.add(world);
        assertThat(msg, is(msg.duplicate()));

        ZMsg reverseMsg = new ZMsg();
        msg.add(hello);
        msg.addFirst(world);
        assertThat(msg.equals(reverseMsg), is(false));
    }

    @Test
    public void testSingleFrameMessage()
    {
        ZContext ctx = new ZContext();

        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zmsg.test");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zmsg.test");

        // Test send and receive of a single ZMsg
        ZMsg msg = new ZMsg();
        ZFrame frame = new ZFrame("Hello");
        msg.addFirst(frame);
        assertThat(msg.size(), is(1));
        assertThat(msg.contentSize(), is(5L));
        msg.send(output);

        ZMsg msg2 = ZMsg.recvMsg(input);
        assertThat(msg2, notNullValue());
        assertThat(msg2.size(), is(1));
        assertThat(msg2.contentSize(), is(5L));

        msg.destroy();
        msg2.destroy();
        ctx.close();
    }

    @Test
    public void testMultiPart()
    {
        ZContext ctx = new ZContext();

        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zmsg.test2");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zmsg.test2");

        ZMsg msg = new ZMsg();
        for (int i = 0; i < 10; i++) {
            msg.addString("Frame" + i);
        }
        ZMsg copy = msg.duplicate();
        copy.send(output);
        msg.send(output);

        copy = ZMsg.recvMsg(input);
        assertThat(copy, notNullValue());
        assertThat(copy.size(), is(10));
        assertThat(copy.contentSize(), is(60L));
        copy.destroy();

        msg = ZMsg.recvMsg(input);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(10));
        int count = 0;
        for (ZFrame f : msg) {
            assertThat(f.streq("Frame" + count++), is(true));
        }
        assertThat(msg.contentSize(), is(60L));
        msg.destroy();

        ctx.close();
    }

    @Test
    public void testMessageFrameManipulation()
    {
        ZMsg msg = new ZMsg();
        for (int i = 0; i < 10; i++) {
            msg.addString("Frame" + i);
        }

        // Remove all frames apart from the first and last one
        for (int i = 0; i < 8; i++) {
            Iterator<ZFrame> iter = msg.iterator();
            iter.next(); // Skip first frame
            ZFrame f = iter.next();
            msg.remove(f);
            f.destroy();
        }

        assertThat(msg.size(), is(2));
        assertThat(msg.contentSize(), is(12L));
        assertThat(msg.getFirst().streq("Frame0"), is(true));
        assertThat(msg.getLast().streq("Frame9"), is(true));

        ZFrame f = new ZFrame("Address");
        msg.push(f);
        assertThat(msg.size(), is(3));
        assertThat(msg.getFirst().streq("Address"), is(true));

        msg.addString("Body");
        assertThat(msg.size(), is(4));
        ZFrame f0 = msg.pop();
        assertThat(f0.streq("Address"), is(true));

        msg.destroy();

        msg = new ZMsg();
        f = new ZFrame("Address");
        msg.wrap(f);
        assertThat(msg.size(), is(2));
        msg.addString("Body");
        assertThat(msg.size(), is(3));
        f = msg.unwrap();
        f.destroy();
        assertThat(msg.size(), is(1));
        msg.destroy();

    }

    @Test
    public void testEmptyMessage()
    {
        ZMsg msg = new ZMsg();
        assertThat(msg.size(), is(0));
        assertThat(msg.getFirst(), nullValue());
        assertThat(msg.getLast(), nullValue());
        assertThat(msg.isEmpty(), is(true));
        assertThat(msg.pop(), nullValue());
        assertThat(msg.removeFirst(), nullValue());
        assertThat(msg.removeFirstOccurrence(null), is(false));
        assertThat(msg.removeLast(), nullValue());

        msg.destroy();

    }

    @Test
    public void testLoadSave()
    {
        ZMsg msg = new ZMsg();
        for (int i = 0; i < 10; i++) {
            msg.addString("Frame" + i);
        }

        try {
            // Save msg to a file
            File f = new File(TemporaryFolderFinder.resolve("zmsg.test"));
            DataOutputStream dos = new DataOutputStream(Files.newOutputStream(f.toPath()));
            assertThat(ZMsg.save(msg, dos), is(true));
            dos.close();

            // Read msg out of the file
            DataInputStream dis = new DataInputStream(Files.newInputStream(f.toPath()));
            ZMsg msg2 = ZMsg.load(dis);
            dis.close();
            f.delete();

            assertThat(msg2.size(), is(10));
            assertThat(msg2.contentSize(), is(60L));

        }
        catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testNewStringMessage()
    {
        // A single string => frame
        ZMsg msg = ZMsg.newStringMsg("Foo");
        assertThat(msg.size(), is(1));
        assertThat(msg.getFirst().streq("Foo"), is(true));

        // Multiple strings => frames
        ZMsg msg2 = ZMsg.newStringMsg("Foo", "Bar", "Baz");
        assertThat(msg2.size(), is(3));
        assertThat(msg2.getFirst().streq("Foo"), is(true));
        assertThat(msg2.getLast().streq("Baz"), is(true));

        // Empty message (Not very useful)
        ZMsg msg3 = ZMsg.newStringMsg();
        assertThat(msg3.isEmpty(), is(true));
    }

    @Test
    public void testClosedContext()
    {
        ZContext ctx = new ZContext();

        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zmsg.test");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zmsg.test");

        ZMsg msg = ZMsg.newStringMsg("Foo", "Bar");
        msg.send(output);

        ZMsg msg2 = ZMsg.recvMsg(input);
        assertThat(msg2.popString(), is("Foo"));
        assertThat(msg2.popString(), is("Bar"));
        msg2.destroy();

        msg.send(output);
        msg.destroy();
        ctx.close();

        try {
            ZMsg.recvMsg(input);
            fail();
        }
        catch (ZMQException e) {
            assertThat(e.getErrorCode(), anyOf(is(ZError.ETERM), is(ZError.EINTR)));
        }
    }
}
