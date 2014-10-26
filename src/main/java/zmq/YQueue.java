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

public class YQueue<T>
{
    //  Individual memory chunk to hold N elements.
    private static class Chunk<T>
    {
        final T[] values;
        final int[] pos;
        Chunk<T> prev;
        Chunk<T> next;

        @SuppressWarnings("unchecked")
        public Chunk(int size, int memoryPtr)
        {
            values = (T[]) new Object[size];
            pos = new int[size];
            for (int i = 0; i != values.length; i++) {
                pos[i] = memoryPtr;
                memoryPtr++;
            }
        }
    };

    //  Back position may point to invalid memory if the queue is empty,
    //  while begin & end positions are always valid. Begin position is
    //  accessed exclusively be queue reader (front/pop), while back and
    //  end positions are accessed exclusively by queue writer (back/push).
    private Chunk<T> beginChunk;
    private int beginPos;
    private Chunk<T> backChunk;
    private int backPos;
    private Chunk<T> endChunk;
    private int endPos;
    private volatile Chunk<T> spareChunk;
    private final int size;

    //  People are likely to produce and consume at similar rates.  In
    //  this scenario holding onto the most recently freed chunk saves
    //  us from having to call malloc/free.
    private int memoryPtr;

    public YQueue(int size)
    {
        this.size = size;
        memoryPtr = 0;
        beginChunk = new Chunk<T>(size, memoryPtr);
        memoryPtr += size;
        beginPos = 0;
        backPos = 0;
        backChunk = beginChunk;
        spareChunk = beginChunk;
        endChunk = beginChunk;
        endPos = 1;
    }

    public int front_pos()
    {
        return beginChunk.pos[beginPos];
    }

    //  Returns reference to the front element of the queue.
    //  If the queue is empty, behaviour is undefined.
    public T front()
    {
        return beginChunk.values[beginPos];
    }

    public int back_pos()
    {
        return backChunk.pos[backPos];
    }

    //  Returns reference to the back element of the queue.
    //  If the queue is empty, behaviour is undefined.
    public T back()
    {
        return backChunk.values[backPos];
    }

    public T pop()
    {
        T val = beginChunk.values[beginPos];
        beginChunk.values[beginPos] = null;
        beginPos++;
        if (beginPos == size) {
            beginChunk = beginChunk.next;
            beginChunk.prev = null;
            beginPos = 0;
        }
        return val;
    }

    //  Adds an element to the back end of the queue.
    public void push(T val)
    {
        backChunk.values[backPos] = val;
        backChunk = endChunk;
        backPos = endPos;

        endPos++;
        if (endPos != size) {
            return;
        }

        Chunk<T> sc = spareChunk;
        if (sc != beginChunk) {
            spareChunk = spareChunk.next;
            endChunk.next = sc;
            sc.prev = endChunk;
        }
        else {
            endChunk.next = new Chunk<T>(size, memoryPtr);
            memoryPtr += size;
            endChunk.next.prev = endChunk;
        }
        endChunk = endChunk.next;
        endPos = 0;
    }

    //  Removes element from the back end of the queue. In other words
    //  it rollbacks last push to the queue. Take care: Caller is
    //  responsible for destroying the object being unpushed.
    //  The caller must also guarantee that the queue isn't empty when
    //  unpush is called. It cannot be done automatically as the read
    //  side of the queue can be managed by different, completely
    //  unsynchronised thread.
    public void unpush()
    {
        //  First, move 'back' one position backwards.
        if (backPos > 0) {
            backPos--;
        }
        else {
            backPos = size - 1;
            backChunk = backChunk.prev;
        }

        //  Now, move 'end' position backwards. Note that obsolete end chunk
        //  is not used as a spare chunk. The analysis shows that doing so
        //  would require free and atomic operation per chunk deallocated
        //  instead of a simple free.
        if (endPos > 0) {
            endPos--;
        }
        else {
            endPos = size - 1;
            endChunk = endChunk.prev;
            endChunk.next = null;
        }
    }
}
