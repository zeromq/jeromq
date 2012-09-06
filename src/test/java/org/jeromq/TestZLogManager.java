/*
    Copyright other contributors as noted in the AUTHORS file.
                
    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.
            
    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.
        
    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.jeromq;


import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.jeromq.ZMQ.Msg;
import org.junit.Before;
import org.junit.Test;

import zmq.Utils;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestZLogManager {

    private String datadir = ".tmp";
    
    @Before
    public void setUpt() {
        File f = new File(datadir, "new_topic");
        Utils.delete(f);
        ZLogManager.instance().getConfig()
            .set("base_dir", datadir)
            .set("segment_size", 2048);
        ZLogManager.instance().get("new_topic").reset();
    }
    @Test
    public void testNewTopic() throws Exception {
        ZLogManager m = ZLogManager.instance();
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
        ZLogManager m = ZLogManager.instance();
        m.getConfig().set("segment_size", 12L)
                .set("flush_interval", 10000L); // do not flush
        ZLog log = m.get("new_topic");
        
        assertThat(log.start(), is(0L));
        assertThat(log.offset(), is(0L));
        
        log.append(new Msg("12345"));
        assertThat(log.count(), is(1));
        long pos = log.append(new Msg("1234567890"));
        assertThat(log.count(), is(2));
        assertThat(pos, is(19L));
        assertThat(log.offset(), is(19L));
        log.close();
    }
    
    @SuppressWarnings("resource")
    @Test
    public void testRecover() throws Exception {
        ZLogManager m = ZLogManager.instance();
        m.shutdown();

        String path = datadir + "/new_topic/00000000000000000000.zmq";
        FileChannel ch = new RandomAccessFile(path, "rw").getChannel();
        MappedByteBuffer buf = ch.map(MapMode.READ_WRITE, 0, m.getConfig().segment_size);
        buf.put((byte)5);
        buf.put((byte)0);
        buf.put("12345".getBytes());
        buf.put((byte)11);
        buf.put((byte)0);
        buf.put("67890abcdef".getBytes());
        ch.close();
        
        ZLog log = m.get("new_topic");
        assertThat(log.offset(), is(20L));
        
        log.append(new Msg("hello"));
        assertThat(log.offset(), is(27L));

    }
}
