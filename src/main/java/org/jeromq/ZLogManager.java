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
import java.lang.reflect.Field;

public class ZLogManager {

    private ZLogConfig conf;
    private static ThreadLocal<Boolean> initialized = new ThreadLocal<Boolean>(); 
    private static ZLogManager instance = null;

    public static class ZLogConfig {
        protected String base_dir;
        protected long segment_size;
        protected File base_path;
        
        private ZLogConfig() {
            set("base_dir", System.getProperty("java.io.tmpdir"));
            set("segment_size", 2L*1024L*1024L*1024L); // 2G
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
            if ("base_dir".equals(name)) {
                File file = new File((String)value);
                if (file.isFile()) {
                    throw new IllegalArgumentException("base_dir " + value + " cannot be file");
                }
                if (!file.exists()) {
                    file.mkdirs();
                }
                
                assert (file.isDirectory());
                base_path = file;
            }
        }
    }
    public ZLogManager() {
        conf = new ZLogConfig();
    }
    
    public ZLogConfig getConfig() {
        return conf;
    }
    
    // A ZLog instance must not be shared between thread
    // It is highly recommended that a single thread who own a single ZMQ worker socket also own ZLog instances
    public ZLog get(String topic) {
        return new ZLog(getConfig(), topic);
    }
    
    public static ZLogManager instance() {
        if (initialized.get() == null) {
            synchronized(initialized) {
                if (instance == null)
                    instance = new ZLogManager(); 
                initialized.set(Boolean.TRUE);
            }
        }
        return instance;
    }

}
