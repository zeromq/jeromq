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
package org.jeromq.codec;

import java.nio.ByteBuffer;

import zmq.DecoderBase;
import zmq.EncoderBase;
import zmq.Msg;

/**
 * Proxy is mostly used when a client can't use zmq socket at any reason.
 * 
 * By extending the ProxyDecoder and ProxyEncoder you can translate non-zmq data into zmq.MSG and vice versa.
 */
public class Proxy {

    public static abstract class ProxyDecoder extends DecoderBase
    {

        private final static int read_header = 0;
        private final static int read_body = 1;
        
        private byte[] header;
        private Msg msg;
        private int size = -1;
        private boolean identity_sent = false;
        private Msg bottom ;
        
        public ProxyDecoder(int bufsize_, long maxmsgsize_) {
            super(bufsize_, maxmsgsize_);
            
            header = new byte[headerSize()];

            next_step(header, header.length, read_header);
            
            bottom = new Msg();
            bottom.set_flags (Msg.more);
            
        }
        
        abstract protected int parseHeader(byte[] header);
        abstract protected boolean parseBody(byte[] body);
        abstract protected int headerSize() ;
            
        protected byte[] getIdentity() {
            return null;
        }
        
        protected boolean preserveHeader() {
            return false;
        }
        
        @Override
        protected boolean next() {
            switch (state()) {
            case read_header:
                return readHeader();
            case read_body:
                return readBody();
            }
            return false;
        }

        private boolean readHeader() {
            size = parseHeader(header);
            
            if (size < 0) {
                decoding_error();
                return false;
            }
            
            msg = new Msg(size);
            
            next_step(msg, read_body);
            
            return true;
        }

        private boolean readBody() {
            
            if (session == null)
                return false;
            
            if (!parseBody(msg.data())) {
                decoding_error();
                return false;
            }
            
            if (!identity_sent) {
                Msg identity = new Msg(getIdentity());
                session.write(identity);
                identity_sent = true;
            }
            
            session.write(bottom);
            
            if (preserveHeader()) {
                Msg hmsg = new Msg(header, true);
                hmsg.set_flags(Msg.more);
                session.write(hmsg);
            }
            session.write(msg);
            
            next_step(header, headerSize(), read_header);
            return true;
        }

        @Override
        public boolean stalled() {
            return state() == read_body;
        }
        
    }

    public static abstract class ProxyEncoder extends EncoderBase
    {

        private final static int write_header = 0;
        private final static int write_body = 1;
        
        private ByteBuffer header ;
        private Msg msg;
        private boolean message_ready;
        private boolean identity_received;
        
        public ProxyEncoder(int bufsize_) {
            super(bufsize_);
            next_step(null, write_header, true);
            message_ready = false;
            identity_received = false;
            
            header = ByteBuffer.allocate(headerSize());
        }

        abstract protected byte[] getHeader(byte[] body);
        abstract protected int headerSize() ;
        
        
        @Override
        protected boolean next() {
            switch (state()) {
            case write_header:
                return write_header();
            case write_body:
                return write_body();
            }
            return false;
        }

        private boolean write_body() {
            next_step(msg, write_header, !msg.has_more());
            
            return true;
        }

        private boolean write_header() {
            
            if (session == null)
                return false;
            
            msg = session.read();
            
            if (msg == null) {
                return false;
            }
            if (!identity_received) {
                identity_received = true;
                msg = session.read();
                if (msg == null)
                    return false;
            }
            
            if (!message_ready) {
                message_ready = true;
                msg = session.read();
                if (msg == null) {
                    return false;
                }
            }
            
            message_ready = false;

            byte[] hbuf = getHeader(msg.data());
            if (hbuf != null) {
                header.clear();
                header.put(hbuf);
                header.flip();
                next_step(header, header.remaining(), write_body, false);
            } else {
                next_step(hbuf, 0, write_body, false);
            }
            return true;
        }

        
        
    }
}
