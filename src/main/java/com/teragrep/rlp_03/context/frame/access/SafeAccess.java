package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class SafeAccess implements AutoCloseable, Supplier<SafeAccess> {

    private final AtomicLong accessCount;
    SafeAccess() {
        accessCount = new AtomicLong();
    }

    @Override
    public SafeAccess get() {
        long endValue = accessCount.incrementAndGet();

        if (endValue < 1) {
            throw new IllegalStateException("accessCount less than 1 after increment");
        }
        return this;
    }

    @Override
    public void close() throws Exception {
        long endValue = accessCount.decrementAndGet();

        if (endValue < 0) {
            throw new IllegalStateException("accessCount less than 0 after decrement");
        }
    }

    public void terminate() {
        boolean endValue = accessCount.compareAndSet(0, -1);
        if(!endValue) {
            throw new IllegalStateException("accessCount not 0");
        }
    }
}
