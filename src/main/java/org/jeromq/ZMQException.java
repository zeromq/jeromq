/*
    Copyright (c) 1991-2011 iMatix Corporation <www.imatix.com>
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

public class ZMQException extends RuntimeException {

    public static class IOException extends RuntimeException {

        private static final long serialVersionUID = 8440355423370109164L;

        public IOException(java.io.IOException cause) {
            super(cause);
        }
    }

    public static class CtxTerminated extends ZMQException {

        private static final long serialVersionUID = 7604861505419474892L;

        public CtxTerminated() {
            super(zmq.ZError.ETERM);
        }

    }

    private static final long serialVersionUID = 5957233088499712341L;

    private final long code;
    
    public ZMQException(int errno) {
        super("Errno " + errno);
        code = errno;
    }

    public long getErrorCode() {
        return code;
    }
    

}
