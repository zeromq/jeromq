/*
 * =========================================================================
 *     ValueReference.java
 *
 *     -------------------------------------------------------------------------
 *     Copyright (c) 2012-2013 InfiniLoop Corporation
 *     Copyright other contributors as noted in the AUTHORS file.
 *
 *     This file is part of JeroMQ
 *
 *     This is free software; you can redistribute it and/or modify it under
 *     the terms of the GNU Lesser General Public License as published by the
 *     Free Software Foundation; either version 3 of the License, or (at your
 *     option) any later version.
 *
 *     This software is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTA-
 *     BILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 *     Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program. If not, see http://www.gnu.org/licenses/.
 *     =========================================================================
 */

package zmq;

public class ValueReference<V>
{
    private V value;

    public ValueReference(V value)
    {
        this.value = value;
    }

    public ValueReference()
    {
    }

    public final V get()
    {
        return value;
    }

    public final void set(V value)
    {
        this.value = value;
    }
}
