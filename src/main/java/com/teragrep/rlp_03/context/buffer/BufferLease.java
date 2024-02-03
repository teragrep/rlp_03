package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// FIXME create tests
public class BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferPool.class);

    private final ByteBuffer buffer;
    private final AtomicBoolean unwrapped;
    private final AtomicLong refCount;
    public BufferLease(ByteBuffer buffer){
        this.buffer = buffer;

        this.unwrapped = new AtomicBoolean();
        this.refCount = new AtomicLong();
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    ByteBuffer release() {
        if (!isRefCountZero()) {
            LOGGER.error("buffer has still references!");
            throw new IllegalStateException("BufferLease has non zero refCount");
        }

        ByteBuffer rv;
        if (unwrapped.compareAndSet(false, true)) {
            buffer.clear();
            rv = buffer;
        }
        else {
            LOGGER.error("buffer is already released!");
            throw new IllegalStateException("BufferLease already released");
        }
        return rv;
    }

    public void addRef() {
        refCount.incrementAndGet();
    }

    public void removeRef() {
        refCount.decrementAndGet();
    }

    boolean isRefCountZero() {
        return refCount.get() == 0;
    }


    @Override
    public String toString() {
        return "BufferLease{" +
                "buffer=" + buffer +
                ", unwrapped=" + unwrapped +
                ", refCount=" + refCount +
                '}';
    }
}
