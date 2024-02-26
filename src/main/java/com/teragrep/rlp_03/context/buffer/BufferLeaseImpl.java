/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021  Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */

package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BufferLeaseImpl implements BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLeaseImpl.class);
    private final long id;
    private final ByteBuffer buffer;
    private long refCount; // TODO consider using a semaphore
    private final Lock lock;

    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.refCount = 0;
        this.lock = new ReentrantLock();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public long refs() {
        lock.lock();
        try {
            return refCount;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public ByteBuffer buffer() {
        lock.lock();
        try {
            return buffer;
        }
        finally {
            lock.unlock();
        }

    }

    @Override
    public void addRef() {
        lock.lock();
        try {
            refCount++;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void removeRef() {
        lock.lock();
        try {

            long newRefs = refCount - 1;
            if (newRefs < 0) {
                throw new IllegalStateException("refs must not be negative");
            }

            refCount = newRefs;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRefCountZero() {
        lock.lock();
        try {
            return refCount == 0;
        }
        finally {
            lock.unlock();
        }
    }


    @Override
    public String toString() {
        lock.lock();
        try {
            return "BufferLease{" +
                    "buffer=" + buffer +
                    ", refCount=" + refCount +
                    '}';
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }

    @Override
    public boolean attemptRelease() {
        lock.lock();
        try {
            boolean rv = false;
            removeRef();
            if (isRefCountZero()) {
                buffer().clear();
                rv = true;
            }
            return rv;

        } finally {
            lock.unlock();
        }
    }
}
