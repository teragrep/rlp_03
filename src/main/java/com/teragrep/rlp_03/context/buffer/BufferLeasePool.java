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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

// FIXME create tests
public class BufferLeasePool {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLeasePool.class);

    private final Supplier<ByteBuffer> byteBufferSupplier;

    private final ConcurrentLinkedQueue<BufferLease> queue;

    private final BufferLease bufferLeaseStub;
    private final AtomicBoolean close;

    private final int segmentSize;

    private final AtomicLong bufferId;

    private final Lock lock;

    // TODO check locking pattern, addRef in BufferLease can escape offer's check and cause dirty in pool?
    public BufferLeasePool() {
        this.segmentSize = 4096;
        this.byteBufferSupplier = () -> ByteBuffer.allocateDirect(segmentSize); // TODO configurable extents
        this.queue = new ConcurrentLinkedQueue<>();
        this.bufferLeaseStub = new BufferLeaseStub();
        this.close = new AtomicBoolean();
        this.bufferId = new AtomicLong();
        this.lock = new ReentrantLock();
    }

    private BufferLease take() {
        // get or create
        BufferLease bufferLease = queue.poll();
        if (bufferLease == null) {
            bufferLease = new BufferLeaseImpl(bufferId.incrementAndGet(), byteBufferSupplier.get());
        }

        bufferLease.addRef(); // all start with one ref

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("returning bufferLease id <{}> with refs <{}> at buffer position <{}>", bufferLease.id(), bufferLease.refs(), bufferLease.buffer().position());
        }
        return bufferLease;

    }

    public List<BufferLease> take(long size) {
        if (close.get()) {
            return Collections.singletonList(bufferLeaseStub);
        }

        LOGGER.debug("requesting take with size <{}>", size);
        long currentSize = 0;
        List<BufferLease> bufferLeases = new LinkedList<>();
        while (currentSize < size) {
            BufferLease bufferLease = take();
            bufferLeases.add(bufferLease);
            currentSize = currentSize + bufferLease.buffer().capacity();

        }
        return bufferLeases;

    }

    public void offer(BufferLease bufferLease) {
        if (bufferLease.attemptRelease()) {
            internalOffer(bufferLease);
        }
    }

    private void internalOffer(BufferLease bufferLease) {
        if (!bufferLease.isStub()) {
            queue.add(bufferLease);
        }

        if (close.get()) {
            LOGGER.debug("closing in offer");
            while (queue.peek() != null) {
                if (lock.tryLock()) {
                    while (true) {
                        BufferLease queuedBufferLease = queue.poll();
                        if (queuedBufferLease == null) {
                            break;
                        }
                    }
                    lock.unlock();
                } else {
                    break;
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            long queueSegments = queue.size();
            long queueBytes = queueSegments * segmentSize;
            LOGGER.debug("offer complete, queueSegments <{}>, queueBytes <{}>", queueSegments, queueBytes);
        }
    }

    public void close() {
        LOGGER.debug("close called");
        close.set(true);

        // close all that are in the pool right now
        internalOffer(bufferLeaseStub);

    }

    public int estimatedSize() {
        return queue.size();
    }
}
