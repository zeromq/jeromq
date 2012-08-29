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
package org.jeromq;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;

import org.jeromq.ZMQ.Msg;


public class ZLog {

    private final static String SUFFIX = ".zmq";
    
    private final String topic;

    private final ZLogManager.ZLogConfig conf;
    private final File path;
    private final long segmentSize;
    private long start;
    private long offset;
    private long pendingMessages;
    private long lastFlush;

    private Deque<Segment> segments = new ArrayDeque<Segment>();
    private Segment current;
    
    public ZLog(ZLogManager.ZLogConfig conf, String topic) {

        this.topic = topic;
        this.conf = conf;
        this.segmentSize = conf.segment_size;
        
        start = 0L;
        offset = 0L;
        pendingMessages = 0L;
        lastFlush = System.currentTimeMillis();
        current = null;
        
        path = new File(conf.base_path, topic);
        if (!path.exists())
            path.mkdirs();
        
        File [] files = path.listFiles();
        Arrays.sort(files, 
                new Comparator<File>() {
                    @Override
                    public int compare(File arg0, File arg1) {
                        return arg0.compareTo(arg1);
                    }
        });
        for (File f: files) {
            offset = Long.valueOf(f.getName());
            segments.add(new Segment(this, offset));
        }
        
        if (!segments.isEmpty()) {
            start = segments.getFirst().start();
            current = segments.getLast();
        }
    }

    public File path() {
        return path;
    }
    
    public long segmentSize() {
        return segmentSize;
    }
    
    public int count() {
        return segments.size();
    }
    
    public long start() {
        return start;
    }
    
    public long offset() {
        return offset;
    }
    
    public long append(Msg msg) {
        if (current == null) {
            current = new Segment(this, 0L);
            segments.add(current);
        }
        
        MappedByteBuffer buf;
        try {
            buf = current.getBuffer(true);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        
        long size = msg.size() + msg.headerSize();
        if (buf.remaining() < size) {
            current.close();
            offset = current.offset();
            current = new Segment(this, offset);
            segments.add(current);
            try {
                buf = current.getBuffer(true);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        
        buf.put(msg.headerBuf());
        buf.put(msg.buf());
        
        pendingMessages += 1L;
        
        long ret = offset + buf.position();
        tryFlush();
        
        return ret;
    }


    public void flush() {
        if (current == null)
            return;
        current.flush();
        offset = current.offset();
        pendingMessages = 0;
        lastFlush = System.currentTimeMillis();
    }
    

    private void tryFlush() {
        if (pendingMessages >= conf.flush_messages
                || System.currentTimeMillis() - lastFlush >= conf.flush_interval) {
            flush();
        }
    }
    
    public void close() {
        if (current == null)
            return;
        current.close();
    }
    
    private static class Segment {

        private final ZLog zlog;
        private long size;
        private long start;
        private FileChannel channel;
        private MappedByteBuffer buffer;
        private final File path;
        
        public Segment(ZLog zlog, long offset) {
            
            this.zlog = zlog;
            this.start = offset;
            this.size = 0;
            this.path = new File(zlog.path(), getName(offset));
            if (path.exists()) 
                size = path.length();
        }
        
        private static String getName(long offset) {
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMinimumIntegerDigits(20);
            nf.setMaximumFractionDigits(0);
            nf.setGroupingUsed(false);
            return nf.format(offset) + SUFFIX;
        }

        
        public MappedByteBuffer getBuffer () throws IOException {
            return getBuffer(false);
        }
        
        @SuppressWarnings("resource")
        public MappedByteBuffer getBuffer (boolean writable) throws IOException {
            if (buffer != null)
                return buffer;
            
            if (channel == null) {
                if (writable)
                    channel = new RandomAccessFile(path, "rw").getChannel();
                else
                    channel = new FileInputStream(path).getChannel();
            }
            
            MapMode mmode = writable? MapMode.READ_WRITE : MapMode.READ_ONLY;
            buffer = channel.map(mmode, 0, zlog.segmentSize());
            if (writable)
                buffer.position((int)size);
            //buffer.load();
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
}
