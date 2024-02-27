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
        else if (bufferLease.phaser().isTerminated()) {
            // bufferLeases with terminated phaser are re-created with original id and buffer.
            bufferLease = new BufferLeaseImpl(bufferLease.id(), bufferLease.buffer());
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
        //LOGGER.info("internalOffer <{}>", queue.size());
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
