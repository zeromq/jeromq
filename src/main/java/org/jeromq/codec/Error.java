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

import zmq.EncoderBase;
import zmq.IMsgSource;

/*
 * Always close the connected socket if there's any message
 */
public class Error {

    public static class ErrorEncoder extends EncoderBase {
        
        public ErrorEncoder (int bufsize_, IMsgSource session, int version)
        {
            super (bufsize_);
        }

        @Override
        protected boolean next() 
        {
            encoding_error ();
            return false;
        }

        @Override
        public void set_msg_source (IMsgSource msg_source_)
        {
        }

    }
}
