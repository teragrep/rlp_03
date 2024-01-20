package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BufferLease {

    private final ByteBuffer buffer;
    private final AtomicBoolean unwrapped;
    private final AtomicLong refCount;
    BufferLease(ByteBuffer byteBuffer){
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

    boolean isRefCountZero() {
        return refCount.decrementAndGet() == 0;
    }

    boolean hasRemaining() {
        return buffer.hasRemaining();
    }
}
