package org.jeromq;


import java.io.File;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.jeromq.ZMQ.Msg;
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
        ZLog log = m.get("new_topic");
        
        assertThat(log.start(), is(0L));
        assertThat(log.offset(), is(0L));
        
        long pos = log.append(new Msg("hello"));
        assertThat(pos, is(7L));
        log.flush();
        
        assertThat(log.offset(), is(7L));
        log.close();
    }
    
    @Test
    public void testOverflow() throws Exception {
        ZLogManager m = new ZLogManager(datadir, 12);
        ZLog log = m.get("new_topic");
        
        assertThat(log.start(), is(0L));
        assertThat(log.offset(), is(0L));
        
        log.append(new Msg("12345"));
        assertThat(log.count(), is(1));
        long pos = log.append(new Msg("1234567890"));
        assertThat(log.count(), is(2));
        assertThat(pos, is(19L));
        assertThat(log.offset(), is(7L));
        log.flush();
        assertThat(log.offset(), is(19L));
        log.close();
    }
}
