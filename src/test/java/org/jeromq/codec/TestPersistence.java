package org.jeromq.codec;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.junit.Test;

import static org.junit.Assert.*;

import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Context;
import org.jeromq.ZMQ.Socket;

public class TestPersistence
{
    @Test
    public void testError ()  throws Exception {
        
        Context ctx = ZMQ.context (1);
        Socket router = ctx.socket (ZMQ.ROUTER);
        router.bind ("tcp://127.0.0.1:6001");
        router.setEncoder (Persistence.PersistEncoder.class);
        
        Socket dealer = ctx.socket (ZMQ.DEALER);
        dealer.setIdentity ("A");
        dealer.connect ("tcp://127.0.0.1:6001");
        
        Thread.sleep (1000);
        router.sendMore ("A");
        router.sendMore (new byte [] {Persistence.MESSAGE_ERROR});
        router.send (new byte [] {Persistence.STATUS_INTERNAL_ERROR});
        
        assertEquals (dealer.recv ()[0], Persistence.STATUS_INTERNAL_ERROR);

        dealer.close ();
        router.close ();
        ctx.term ();
    }
    
    @Test
    public void testResponse ()  throws Exception {
        
        Context ctx = ZMQ.context (1);
        Socket router = ctx.socket (ZMQ.ROUTER);
        router.bind ("tcp://127.0.0.1:6001");
        router.setEncoder (Persistence.PersistEncoder.class);
        
        Socket dealer = ctx.socket (ZMQ.DEALER);
        dealer.setIdentity ("A");
        dealer.connect ("tcp://127.0.0.1:6001");
        
        Thread.sleep (1000);
        router.sendMore ("A");
        router.sendMore (new byte [] {Persistence.MESSAGE_RESPONSE});
        router.sendMore (new byte [] {Persistence.STATUS_OK});
        router.send (ByteBuffer.wrap (new byte [8]).putLong (100).array ());
        
        assertEquals (dealer.recv ()[0], Persistence.STATUS_OK);
        assertEquals (dealer.recv ().length, 8);

        dealer.close ();
        router.close ();
        ctx.term ();
    }

    @Test
    public void testFile ()  throws Exception {
        
        String datadir = ".tmp";
        String longdata = "";
        for (int i = 0; i < 30; i++)
            longdata += "1234567890";
        String path = datadir + "/new_topic/00000000000000000000.dat";
        RandomAccessFile raf = new RandomAccessFile(path, "rw");
        FileChannel ch = raf.getChannel ();
        MappedByteBuffer buf = ch.map (MapMode.READ_WRITE, 0, 1024);
        buf.put ((byte) 0);   
        buf.put ((byte) 5);
        buf.put ("12345".getBytes ());
        buf.put ((byte) 1);   // more
        buf.put ((byte) 11);
        buf.put ("67890abcdef".getBytes ());
        buf.put ((byte) 2);   // long
        buf.putLong (300);
        buf.put (longdata.getBytes ());

        raf.close();
        ch.close ();
        
        Context ctx = ZMQ.context (1);
        Socket router = ctx.socket (ZMQ.ROUTER);
        router.bind ("tcp://127.0.0.1:6001");
        router.setEncoder (Persistence.PersistEncoder.class);
        
        Socket dealer = ctx.socket (ZMQ.DEALER);
        dealer.setIdentity ("A");
        dealer.connect ("tcp://127.0.0.1:6001");
        
        Thread.sleep (1000);
        router.sendMore ("A");
        router.sendMore (new byte [] {Persistence.MESSAGE_FILE});
        router.sendMore (new byte [] {Persistence.STATUS_OK});
        router.sendMore (path);
        router.sendMore (ByteBuffer.wrap (new byte [8]).putLong (0).array ());
        router.send (ByteBuffer.wrap (new byte [8]).putLong (329).array ());
        
        assertEquals (dealer.recv ()[0], Persistence.STATUS_OK);
        ByteBuffer content = ByteBuffer.wrap (dealer.recv ());

        assertEquals (content.limit (), 329);
        assertEquals (0, content.get ());
        int length = content.get ();
        assertEquals (5, length);
        byte [] data = new byte [length];
        content.get (data);
        assertEquals ("12345", new String (data));

        assertEquals (1, content.get ());
        length = content.get ();
        assertEquals (11, length);
        data = new byte [length];
        content.get (data);
        assertEquals ("67890abcdef", new String (data));
        
        assertEquals (2, content.get ());
        length = (int) content.getLong ();
        assertEquals (300, length);
        data = new byte [length];
        content.get (data);
        assertEquals (longdata, new String (data));

        assertEquals (false, content.hasRemaining ());

        dealer.close ();
        router.close ();
        ctx.term ();
    }
}
