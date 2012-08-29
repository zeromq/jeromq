/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
    Copyright (c) 2011-2012 Spotify AB
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

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


public class ZException  {


    public static class AddrInUse extends RuntimeException {

        private static final long serialVersionUID = 8574207129098333986L;

    }
    public static class NoFreeSlot extends RuntimeException {
        private static final long serialVersionUID = -8599234879594535002L;
    }

    public static class ConnectionRefused extends RuntimeException {
        private static final long serialVersionUID = 2962118056167320315L;
    }
    
    public static class CtxTerminated extends RuntimeException {

        private static final long serialVersionUID = 7074545575003946832L;
        
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

}
