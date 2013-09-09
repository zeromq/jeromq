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
package zmq;

import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class ZError  {

    public static class CtxTerminatedException extends RuntimeException {
        private static final long serialVersionUID = -4404921838608052956L;

        public CtxTerminatedException() {
            super();
        }
    }

    public static class InstantiationException extends RuntimeException {
        private static final long serialVersionUID = -4404921838608052955L;
        
        public InstantiationException(Throwable cause) {
            super(cause);
        }
    }

    public static class IOException extends RuntimeException {
        private static final long serialVersionUID = 9202470691157986262L;

        public IOException(java.io.IOException e) {
            super(e);
        }
    }

    public static final int EINTR = 4;
    public static final int EACCESS = 13;
    public static final int EFAULT = 14;
    public static final int EINVAL = 22;
    public static final int EAGAIN = 35;
    public static final int EINPROGRESS = 36;
    public static final int EPROTONOSUPPORT = 43;
    public static final int ENOTSUP = 45;
    public static final int EADDRINUSE = 48;
    public static final int EADDRNOTAVAIL = 49;
    public static final int ENETDOWN = 50;
    public static final int ENOBUFS = 55;
    public static final int EISCONN = 56;
    public static final int ENOTCONN = 57;
    public static final int ECONNREFUSED = 61;
    public static final int EHOSTUNREACH = 65;
    
    private static final int ZMQ_HAUSNUMERO = 156384712;

    public static final int EFSM = ZMQ_HAUSNUMERO + 51;
    public static final int ENOCOMPATPROTO = ZMQ_HAUSNUMERO + 52;
    public static final int ETERM = ZMQ_HAUSNUMERO + 53;
    public static final int EMTHREAD = ZMQ_HAUSNUMERO + 54;

    public static final int EIOEXC = ZMQ_HAUSNUMERO + 105;
    public static final int ESOCKET = ZMQ_HAUSNUMERO + 106;
    public static final int EMFILE = ZMQ_HAUSNUMERO + 107;

    public static int exccode (java.io.IOException e) {
        if (e instanceof SocketException) {
            return ESOCKET;
        } else if (e instanceof ClosedChannelException) {
            return ENOTCONN;
        } else {
            return EIOEXC;
        }
    }

    public static String toString(int code)
    {
        switch (code) {
        case EADDRINUSE:
            return "Address already in use";
        case EFSM:
            return "Operation cannot be accomplished in current state";
        case ENOCOMPATPROTO:
            return "The protocol is not compatible with the socket type";
        case ETERM:
            return "Context was terminated";
        case EMTHREAD:
            return "No thread available";
        }
        return "";
    }

}
