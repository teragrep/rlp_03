package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class BufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferPool.class);

    private final Supplier<ByteBuffer> byteBufferSupplier;

    private final ConcurrentLinkedQueue<ByteBuffer> queue;

    private final ByteBuffer byteBufferStub;

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean close;

    public BufferPool() {
        this.byteBufferSupplier = () -> ByteBuffer.allocateDirect(4096); // TODO configurable extents
        this.queue = new ConcurrentLinkedQueue<>();
        this.byteBufferStub = ByteBuffer.allocateDirect(0);
        this.close = new AtomicBoolean();
    }

    ByteBuffer take() {
        ByteBuffer byteBuffer;
        if (close.get()) {
            byteBuffer = byteBufferStub;
        }
        else {
            // get or create
            byteBuffer = queue.poll();
            if (byteBuffer == null) {
                byteBuffer = byteBufferSupplier.get();
            }
        }

        return byteBuffer;
    }

    void offer(BufferLease bufferLease) {
        if (!bufferLease.isRefCountZero()) {
            queue.add(bufferLease.unwrap());
        }

        if (close.get()) {
            while (queue.peek() != null) {
                if (lock.tryLock()) {
                    while (true) {
                        ByteBuffer queuedByteBuffer = queue.poll();
                        if (queuedByteBuffer == null) {
                            break;
                        }
                        // ensuring GC of the buffer, as direct ones are only GC'd in case of normal object is in GC
                        Object object = new Object();
                        LOGGER.trace("Freeing queuedByteBuffer <{}> with tempObject <{}>", queuedByteBuffer, object);
                    }
                    lock.unlock();
                } else {
                    break;
                }
            }
        }
    }

    public void close() {
        close.set(true);

        // close all that are in the pool right now
        offer(new BufferLease(ByteBuffer.allocateDirect(0)));
    }
}
