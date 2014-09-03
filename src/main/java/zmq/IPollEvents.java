/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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

public interface IPollEvents
{
    // Called by I/O thread when file descriptor is ready for reading.
    void inEvent();

    // Called by I/O thread when file descriptor is ready for writing.
    void outEvent();

    // Called by I/O thread when file descriptor might be ready for connecting.
    void connectEvent();

    // Called by I/O thread when file descriptor is ready for accept.
    void acceptEvent();

    // Called when timer expires.
    void timerEvent(int id);
}
