/*
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
package org.zeromq;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.NumberFormat;


public class ZLog {

    private final static String SUFFIX = ".zmq";
    
    private ZLogManager mgr;
    private String topic;
    private String name;
    private File path;
    private long size;
    private long segmentSize;
    private long start;
    private FileChannel channel;
    private MappedByteBuffer buffer;
    
    public ZLog(ZLogManager manager, String topic, long size) {
        this(manager, topic, size, 0);
    }
    
    public ZLog(ZLogManager mgr, String topic, long segmentSize, long offset) {
        this.mgr = mgr;
        this.topic = topic;
        this.segmentSize = segmentSize;
        this.start = offset;
        this.name = getName(offset);
        File base = new File(mgr.getPath(), topic);
        if (!base.exists())
            base.mkdirs();
        this.path = new File(base, name);
        this.size = 0;
        if (path.exists()) 
            size = path.length();
    }

    
    private String getName(long offset) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(20);
        nf.setMaximumFractionDigits(0);
        nf.setGroupingUsed(false);
        return nf.format(offset) + SUFFIX;
    }

    public MappedByteBuffer getBuffer () throws IOException {
        return getBuffer("r");
    }
    
    public MappedByteBuffer getBuffer (String mode) throws IOException {
        if (buffer != null)
            return buffer;
        
        if (channel == null)
            channel = new RandomAccessFile(path, mode).getChannel();
        MapMode mmode = MapMode.READ_ONLY;
        int position = 0;
        if (mode.equals("rw")) {
            mmode = MapMode.READ_WRITE;
            position = (int) size;
        }
        buffer = channel.map(mmode, 0, segmentSize);
        buffer.position(position);
        buffer.load();
        return buffer;
    }


    public long offset() {
        if (buffer == null)
            return start + size;
        return start + buffer.position();
    }
    
    public void flush() {
        if (buffer != null) {
            buffer.force();
            size = buffer.position();
        }
    }

    public long start() {
        return start;
    }

    public void close() {
        if (channel == null)
            return;
        
        flush();
        try {
            channel.truncate(size);
            channel.close();
        } catch (IOException e) {
        }
        channel = null;
        buffer = null;
    }
    
    public void recover() {
        //TODO
    }
}
