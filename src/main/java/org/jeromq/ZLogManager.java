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
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public class ZLogManager {

    private final ZLogConfig conf;
    private final ConcurrentHashMap<String, ZLog> logs;
    
    private static ThreadLocal<Boolean> initialized = new ThreadLocal<Boolean>(); 
    private static ZLogManager instance = null;

    public static class ZLogConfig {
        protected String base_dir;
        protected long segment_size;
        protected long flush_messages;
        protected long flush_interval;
        protected long cleanup_interval;
        protected File base_path;
        
        private ZLogConfig() {
            set("base_dir", System.getProperty("java.io.tmpdir") + "/zlogs");
            set("segment_size", 536870912L); 
            set("flush_messages", 10000) ; 
            set("flush_interval", 1000) ; // 1 sec
            set("cleanup_interval", 604800000L) ; // 1 week
        }
        
        public ZLogConfig set(String name, Object value) {
            Field field;
            try {
                field = ZLogConfig.class.getDeclaredField(name);
            } catch (SecurityException e) {
                throw e;
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(name);
            }
            try {
                field.set(this, value);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(name + " = " + value + " " + e.toString());
            }
            postSet(name, value);
            return this;
        }
        
        public Object get(String name) {
            Field field;
            
            try {
                field = ZLogConfig.class.getDeclaredField(name);
            } catch (SecurityException e) {
                throw e;
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(name);
            }
            
            try {
                return field.get(this);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(name + " " + e.toString());
            }
        }
        
        public String getString(String name) {
            return (String) get(name);
        }
        
        public int getInt(String name) {
            return (Integer) get(name);
        }
        
        public long getLong(String name) {
            return (Long) get(name);
        }

        private void postSet(String name, Object value) {
            File file = null;
            if ("base_dir".equals(name)) {
                if (value == null) {
                    file = new File(System.getProperty("java.io.tmpdir"), "zlogs");
                } else {
                    file = new File((String)value);
                }
                base_dir = file.getAbsolutePath();

                if (file.isFile()) {
                    throw new IllegalArgumentException("base_dir " + value + " cannot be file");
                }
                if (!file.exists()) {
                    if(!file.mkdirs()) {
                        throw new RuntimeException("Cannot make directory " + file.getAbsolutePath());
                    }
                }
                
                assert (file.isDirectory());
                base_path = file;
            }
        }
        
    }
    
    private ZLogManager() {
        conf = new ZLogConfig();
        logs = new ConcurrentHashMap<String, ZLog>();
    }
    
    public ZLogConfig config() {
        return conf;
    }
    
    /**
     * Returns ZLog instance is a singleton
     * Write operations, append, flush, close should be called by a single thread or must be synchronized
     * 
     * @param topic
     * @return ZLog singleton instance
     */
    public ZLog get(String topic) {
        ZLog log = logs.get(topic);
        if (log == null) {
            log = new ZLog(conf, topic);
            ZLog plog = logs.putIfAbsent(topic, log);
            if (plog != null) 
                log = plog;
        }
        return log;
    }
    
    /**
     * 
     * @return array of active topics 
     */
    public String[] topics() {
        return logs.keySet().toArray(new String[0]);
    }
    
    synchronized public void shutdown() {
        for (ZLog log: logs.values()) {
            log.close();
        }
        logs.clear();
    }
    
    public static ZLogManager instance() {
        if (initialized.get() == null) {
            synchronized(initialized) {
                if (instance == null) {
                    instance = new ZLogManager(); 
                }
                initialized.set(Boolean.TRUE);
            }
        }
        return instance;
    }

}
