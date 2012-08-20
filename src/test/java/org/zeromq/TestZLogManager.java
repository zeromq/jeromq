package org.zeromq;


import java.io.File;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.Utils;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestZLogManager {

    private String datadir = "tmp";
    
    @Before
    public void setUpt() {
        File f = new File(datadir, "new_topic");
        Utils.delete(f);
    }
    @Test
    public void testNewTopic() throws Exception {
        ZLogManager m = new ZLogManager(datadir, 1024);
        ZLog log = m.newLog("new_topic");
        
        assertThat(log.start(), is(0L));
        assertThat(log.offset(), is(0L));
        
        ByteBuffer b = log.getBuffer("rw");
        b.put("hello".getBytes());
        log.flush();
        
        assertThat(log.offset(), is(5L));
        log.close();
    }
    
    @Test
    public void testOverflow() throws Exception {
        ZLogManager m = new ZLogManager(datadir, 10);
        ZLog log = m.newLog("new_topic");
        
        assertThat(log.start(), is(0L));
        assertThat(log.offset(), is(0L));
        
        ByteBuffer b = log.getBuffer("rw");
        b.put("12345".getBytes());
        try {
            b.put("1234567890".getBytes());
        } catch (BufferOverflowException e) {
            log = m.newLog("new_topic");
        }
        assertThat(log.start(), is(5L));
        b = log.getBuffer("rw");
        b.put("1234567890".getBytes());
        assertThat(log.offset(), is(15L));
        log.close();
    }
}
