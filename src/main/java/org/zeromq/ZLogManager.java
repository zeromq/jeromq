package org.zeromq;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class ZLogManager {

    private File basedir;
    private long segmentSize;
    private Map<String, Deque<ZLog>> segments; 
    
    public ZLogManager(String dir, long segmentSize) {
        this.basedir = new File(dir);
        this.segmentSize = segmentSize;
        if (!basedir.exists()) {
            basedir.mkdirs();
        }
        segments = new HashMap<String, Deque<ZLog>>();
        loads();
    }
    
    private void loads() {
        for(File tdir: basedir.listFiles()) {
            if (tdir.isDirectory()) {
                addTopicDir(tdir);
            }
        }
    }
    
    private void addTopicDir(File tdir) {
        String topic = tdir.getName();
        Deque<ZLog> logs = new ArrayDeque<ZLog>();
        File [] files = tdir.listFiles();
        Arrays.sort(files, 
                new Comparator<File>() {
                    @Override
                    public int compare(File arg0, File arg1) {
                        return arg0.compareTo(arg1);
                    }
        });
        for (File f: files) {
            long offset = Long.valueOf(f.getName());
            logs.add(new ZLog(this, topic, segmentSize, offset));
        }
        segments.put(topic, logs);
    }

    public ZLog newLog (String topic) {
        Deque<ZLog> logs = segments.get(topic);

        long offset = 0L;
        if (logs == null) {
            logs = new ArrayDeque<ZLog>();
            segments.put(topic, logs);
        } else {
            ZLog last = logs.getLast();
            last.close();
            offset = last.offset();
        }
        ZLog log = new ZLog(this, topic, segmentSize, offset);
        logs.addLast(log);
        
        return log;
    }
    
    public ZLog lastLog (String topic) {
        Deque<ZLog> logs = segments.get(topic);
        
        if (logs == null)
            return null;
        
        return logs.getLast();
    }

    public File getPath() {
        return basedir;
    }
}
