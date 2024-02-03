package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

// FIXME create tests
public class BufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferPool.class);

    private final Supplier<ByteBuffer> byteBufferSupplier;

    private final ConcurrentLinkedQueue<ByteBuffer> queue;

    private final ByteBuffer byteBufferStub;

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean close;

    public BufferPool() {
        this.byteBufferSupplier = () -> ByteBuffer.allocateDirect(2); // TODO configurable extents
        this.queue = new ConcurrentLinkedQueue<>();
        this.byteBufferStub = ByteBuffer.allocateDirect(0);
        this.close = new AtomicBoolean();
    }

    public ByteBuffer take() {
        LOGGER.info("take entry queue.size() <{}>", queue.size());
        ByteBuffer byteBuffer;
        if (close.get()) {
            LOGGER.info("BufferPool closing, not providing new buffers");
            byteBuffer = byteBufferStub;
        }
        else {
            // get or create
            byteBuffer = queue.poll();
            if (byteBuffer == null) {
                byteBuffer = byteBufferSupplier.get();
                LOGGER.info("created new byteBuffer <{}>", byteBuffer);
            }
        }
        LOGGER.info("returning byteBuffer <{}>", byteBuffer);
        LOGGER.info("take exit queue.size() <{}>", queue.size());
        return byteBuffer;
    }

    public ByteBuffer[] take(long size) {
        LOGGER.info("requesting take with size <{}>", size);
        long currentSize = 0;
        List<ByteBuffer> byteBufferList = new LinkedList<>();
        while (currentSize < size) {
            ByteBuffer byteBuffer = take();
            if (byteBufferStub.equals(byteBuffer)) {
                break;
            }
            else {
                byteBufferList.add(byteBuffer);
                currentSize = currentSize + byteBuffer.capacity();
            }
        }

        ByteBuffer[] bufferArray = byteBufferList.toArray(new ByteBuffer[0]);
        LOGGER.info("take returning bufferArray.length() <{}>", bufferArray.length);
        return bufferArray;
    }

    public void offer(BufferLease bufferLease) {
        LOGGER.info("offer received bufferLease <{}>", bufferLease);
        if (!bufferLease.isRefCountZero()) {
            LOGGER.info("returning buffer");
            ByteBuffer buffer = bufferLease.unwrap();
            LOGGER.info("adding buffer <{}> to queue", buffer);
            queue.add(buffer);
        }

        if (close.get()) {
            LOGGER.info("closing in offer");
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
        LOGGER.info("offer complete");
    }

    public void close() {
        LOGGER.info("close called");
        close.set(true);

        // close all that are in the pool right now
        offer(new BufferLease(ByteBuffer.allocateDirect(0)));
    }
}
