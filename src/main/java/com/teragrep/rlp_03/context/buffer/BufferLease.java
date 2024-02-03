package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// FIXME create tests
public class BufferLease {

    private final ByteBuffer buffer;
    private final AtomicBoolean unwrapped;
    private final AtomicLong refCount;
    public BufferLease(ByteBuffer byteBuffer){
        this.buffer = byteBuffer;

        this.unwrapped = new AtomicBoolean();
        this.refCount = new AtomicLong();
    }

    ByteBuffer unwrap() {
        if (!isRefCountZero()) {
            throw new IllegalStateException("BufferLease has non zero refCount");
        }

        ByteBuffer rv;
        if (unwrapped.compareAndSet(false, true)) {
            buffer.clear();
            rv = buffer;
        }
        else {
            throw new IllegalStateException("BufferLease already unwrapped");
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

    boolean hasRemaining() {
        return buffer.hasRemaining();
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
