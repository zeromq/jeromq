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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.jeromq.ZMQ.Msg;


public class ZLog {

    private final static String SUFFIX = ".dat";
    
    private final String topic;

    private final ZLogManager.ZLogConfig conf;
    private File path;
    private long start;
    private long pendingMessages;
    private long lastFlush;

    private final TreeMap<Long, Segment> segments ;
    private Segment current;
    private static final Pattern pattern = Pattern.compile("\\d{20}\\" + SUFFIX);

    private static final long LATEST = -1L;
    private static final long EARLIEST = -2L;
    
    public ZLog(ZLogManager.ZLogConfig conf, String topic) {

        this.topic = topic;
        this.conf = conf;
        
        segments = new TreeMap<Long, Segment>();
        reset();
        recover();
    }
    
    protected void reset() {

        close();

        start = 0L;
        pendingMessages = 0L;
        lastFlush = System.currentTimeMillis();
        current = null;
        
        path = new File(conf.base_path, topic);

        if (!path.exists())
            if(!path.mkdirs()) {
                throw new RuntimeException("Cannot make directory " + path.getAbsolutePath());
            }
        
        File [] files = path.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return pattern.matcher(name).matches();
                    }
                });
        Arrays.sort(files, 
                new Comparator<File>() {
                    @Override
                    public int compare(File arg0, File arg1) {
                        return arg0.compareTo(arg1);
                    }
        });
        segments.clear();
        for (File f: files) {
            long offset = Long.valueOf(f.getName().replace(SUFFIX, ""));
            segments.put(offset, new Segment(this, offset));
        }
        
        if (!segments.isEmpty()) {
            start = segments.firstKey();
            current = segments.lastEntry().getValue();
        }

    }

    public File path() {
        return path;
    }
    
    public long segmentSize() {
        return conf.segment_size;
    }
    
    public int count() {
        return segments.size();
    }
    
    public long start() {
        return start;
    }
    
    public long offset() {
        return current == null? 0L: current.offset();
    }

    /**
     * last element is always safely flushed offset
     * 
     * @return array of segment start offsets
     */
    public long[] offsets() {
        long[] offsets = new long[segments.size()+1];
        int i = 0;
        while (true) {
            // fail first instead of using a lock
            try {
                for (Long key: segments.keySet()) {
                    offsets[i] = key;
                    i++;
                }
                break;
            } catch (ConcurrentModificationException e) {
                // retry
                offsets = new long[segments.size()+1];
            }
        }
        offsets[i] = current == null? 0L : current.size();
        return offsets;
    }

    /**
     * For -1, it always returns start and last safely flushed offset
     * 
     * @param modifiedBefore timestamp in milli. -2 for first, -1 for latest 
     * @param maxEntry maximum entries 
     * @return array of segment start offsets which where modified since
     */
    public long[] offsets(long modifiedBefore, int maxEntry) {
        
        if (segments.isEmpty()) 
            return new long[0] ;
        
        if (modifiedBefore == EARLIEST) // first
            return new long[] { segments.firstKey() };
        if (modifiedBefore == LATEST) {
            Map.Entry<Long, Segment> last = segments.lastEntry();
            return new long [] { last.getKey(), last.getKey() + last.getValue().size() };
        }
        
        Segment[] values;
        
        while (true) {
            // fail first instead of using a lock
            try {
                values = segments.values().toArray(new Segment[0]);
                break;
            } catch (ConcurrentModificationException e) {
                //
            }
        }
        int idx = values.length /2 ;
        int top = values.length;
        int bottom = -1;
        while (idx > bottom && idx < top) {
            Segment v = values[idx];
            long lastMod = v.lastModified();
            
            if (lastMod < modifiedBefore) {
                bottom = idx;
            } else if (lastMod > modifiedBefore) {
                top = idx;
            } else {
                break;
            }
            idx = (top + bottom) / 2;
        }
        if (bottom == -1) { // no matches
            return new long[0];
        }
        int start = 0;
        if (maxEntry > 0 && maxEntry < (idx+1)) {
            start = idx - maxEntry + 1;
        }
        long [] offsets = new long[idx - start + 1 + (top == values.length ? 1: 0)];
        for (int i = start; i <= idx; i++) {
            offsets[i] = values[i].start();
            i++;
        }
        if (top == values.length)
            offsets[offsets.length-1] = current.size();
        return offsets;
    }
    
    /**
     * This operation is not thread-safe. 
     * This should be called by a single thread or must be synchronized by caller
     *      
     * @param msg
     * @return last absolute position
     * @throws IOException
     */
    public long append(Msg msg) throws IOException {
        
        long size = msg.size() + msg.headerSize();
        MappedByteBuffer buf = getBuffer(size, true);
        
        buf.put(msg.headerBuf());
        buf.put(msg.buf());
        
        pendingMessages = pendingMessages + 1L;
        
        tryFlush();
        
        return current.offset();
    }

    private MappedByteBuffer getBuffer(long size, boolean writable) throws IOException {
        
        if (current == null) {
            current = new Segment(this, 0L);
            segments.put(0L, current);
        }
        
        MappedByteBuffer buf = current.getBuffer(writable);
        
        if (buf.remaining() < size) {
            current.close();
            long offset = current.offset();
            current = new Segment(this, offset);
            segments.put(offset, current);
            buf = current.getBuffer(writable);
            cleanup();
        }
        
        return buf;
    }
    
    public List<Msg> readMsg(long start, int max) throws InvalidOffsetException, IOException {
        
        Map.Entry<Long, Segment> entry = segments.floorEntry(start);
        List<Msg> results = new ArrayList<Msg>();
        MappedByteBuffer buf;
        Msg msg;
        
        if (entry == null) {
            return results;
        }
        buf = entry.getValue().getBuffer(false);
        buf.position((int) (start - entry.getKey()));

        while ((msg = readMsg(buf)) != null) {
            max = max - msg.size();
            if (max <= 0)
                break;
            results.add(msg);
        }
        
        return results;
    }

    public int read(long start, ByteBuffer dst) throws IOException {
        Map.Entry<Long, Segment> entry = segments.floorEntry(start);
        FileChannel ch;
        ch = entry.getValue().getChannel(false);
        ch.position(start - entry.getKey());
        return ch.read(dst);
        
    }

    /**
     * By using memory mapped file, returned file channel might not be fully filled.
     * 
     * @param start absolute file offset
     * @return FileChannel
     * @throws IOException
     */
    public FileChannel open(long start) throws IOException {
        Map.Entry<Long, Segment> entry = segments.floorEntry(start);
        FileChannel ch;
        ch = entry.getValue().getChannel(false);
        ch.position(start - entry.getKey());
        
        return ch;
    }

    /**
     * This operation is not thread-safe. 
     * This should be called by a single thread or must be synchronized by caller
     */
    public void flush() {
        if (current == null)
            return;
        current.flush();
        pendingMessages = 0;
        lastFlush = System.currentTimeMillis();
    }
    

    private void tryFlush() {
        boolean flush = false;
        if (pendingMessages >= conf.flush_messages) {
            flush = true;
        }
        if (!flush && System.currentTimeMillis() - lastFlush >= conf.flush_interval) {
            flush = true;
        }
        if (flush)
            flush();
    }
    
    private void recover() {
        if (current != null) {
            try {
                current.recover();
            } catch (IOException e) {
                throw new ZMQException.IOException(e);
            }
        }
    }
    
    private void cleanup() {
        long expire = System.currentTimeMillis() - conf.cleanup_interval;
        
        for (Segment seg: segments.values()) {
            if (seg.lastModified() < expire) {
                if (seg != current)
                    seg.delete();
            }
            break;
        }
    }

    /**
     * This operation is not thread-safe. 
     * This should be called by a single thread or must be synchronized by caller
     */
    public void close() {
        if (current == null)
            return;
        current.close();
        current = null;
    }
    
    @Override
    public String toString () {
        if (current == null) {
            return super.toString() + "[" + topic + "]";
        } else {
            return super.toString() + "[" + topic + "," + current.toString() +"]";
        }
    }
    
    private static class Segment {

        private final ZLog zlog;
        private long size;
        private long start;
        private FileChannel channel;
        private MappedByteBuffer buffer;
        private final File path;
        
        protected Segment(ZLog zlog, long offset) {
            
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


        protected FileChannel getChannel (boolean writable) throws IOException {
            if (writable) {
                if (channel == null)
                    channel = new RandomAccessFile(path, "rw").getChannel();
                return channel;
            } else {
                return new FileInputStream(path).getChannel();
            }
        }
        
        protected MappedByteBuffer getBuffer (boolean writable) throws IOException {
            if (writable && buffer != null)
                return buffer;
            
            FileChannel ch = getChannel(writable);
            
            if (writable) {
                buffer = ch.map(MapMode.READ_WRITE, 0, zlog.segmentSize());
                buffer.position((int)size);
                return buffer;
            } else {
                MappedByteBuffer rbuf = ch.map(MapMode.READ_ONLY, 0, ch.size());
                ch.close();
                return rbuf;
            }
            
        }


        protected final long offset() {
            if (buffer == null)
                return start + size;
            return start + buffer.position();
        }
        
        protected final long size() {
            return size;
        }
        
        
        protected final long start() {
            return size;
        }
        
        protected void flush() {
            if (buffer != null) {
                buffer.force();
                size = buffer.position();
            }
        }

        protected void recover() throws IOException {
            FileChannel ch =  new RandomAccessFile(path, "rw").getChannel();
            FileLock lock = null;
            
            while (true) {
                try {
                    lock = ch.lock();
                    break;
                } catch (OverlappingFileLockException e) {
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            try {
                int pos = 0;
                MappedByteBuffer buf = ch.map(MapMode.READ_ONLY, 0, ch.size());
                while (true) {
                    pos = buf.position();
                    try {
                        Msg msg = readMsg(buf);
                        if (msg == null)
                            break;
                    } catch (InvalidOffsetException e) {
                        // block corrupted
                        break;
                    }
                }
                if (pos < ch.size()) {
                    ch.truncate(pos);
                    size = pos;
                }
            } finally {
                lock.release();
                ch.close();
            }
        }

        protected void close() {
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
        
        protected long lastModified() {
            return path.lastModified();
        }
        
        protected void delete() {
            path.delete();
        }
        
        @Override
        public String toString() {
            return path.getAbsolutePath() + "(" + offset() + ")";
        }
    }
    
    public static class InvalidOffsetException extends Exception {

        private static final long serialVersionUID = -1696298215013570232L;

        public InvalidOffsetException(Throwable e) {
            super(e);
        }
        
        public InvalidOffsetException() {
            super();
        }
        
    }

    private static Msg readMsg(ByteBuffer buf) throws InvalidOffsetException {
        
        if (!buf.hasRemaining())
            return null;
        
        int length;
        int shortLength;
        long longLength;
        byte flag;
        Msg msg = null;
        
        try {
            shortLength = buf.get();

            if (shortLength == 0) { 
                // not used
                return null;
            } else if (shortLength == -1) { // 0xff
                longLength = buf.getLong();
                length = (int)longLength;
                if (length < 255)
                    throw new InvalidOffsetException();
            } else {
                length = shortLength;
                if (length < 0)
                    length = (0xFF) & length;
            }
            if (length > buf.remaining())
                throw new InvalidOffsetException();

            flag = buf.get();  // flag
            if (flag != 0 && flag != Msg.MORE)
                throw new InvalidOffsetException();

            msg = new Msg(length);
            if (flag == Msg.MORE)
                msg.setFlags(Msg.MORE);
            buf.get(msg.data());
        } catch (BufferUnderflowException e) {
            // block corrupted
            throw new InvalidOffsetException(e);
        } catch (IllegalArgumentException e) {
            throw new InvalidOffsetException(e);
        }
        
        return msg;
    }



}
