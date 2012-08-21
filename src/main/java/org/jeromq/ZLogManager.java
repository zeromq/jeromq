package org.jeromq;

import java.io.File;

public class ZLogManager {

    private File basedir;
    private long segmentSize;
    
    public ZLogManager(String dir, long segmentSize) {
        this.basedir = new File(dir);
        this.segmentSize = segmentSize;
        if (!basedir.exists()) {
            basedir.mkdirs();
        }
    }

    // must be called in a thread safe way
    public ZLog get(String topic) {
        return new ZLog(this, topic, segmentSize);
    }
    

    public File path() {
        return basedir;
    }
}
