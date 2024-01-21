package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class SafeAccess implements AutoCloseable, Supplier<SafeAccess> {

    private final AtomicLong accessCount;
    private final AtomicBoolean terminated;
    public SafeAccess() {
        accessCount = new AtomicLong();
        terminated = new AtomicBoolean();
    }

    @Override
    public SafeAccess get() {
        if (terminated.get()) {
            throw new IllegalStateException("already terminated");
        }

        long endValue = accessCount.incrementAndGet();

        if (endValue < 1) {
            throw new IllegalStateException("accessCount less than 1 after increment");
        }
        return this;
    }

    @Override
    public void close() {
        if (terminated.get()) {
            throw new IllegalStateException("already terminated");
        }

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
        else {
            if (!terminated.compareAndSet(false, true)) {
                throw new IllegalStateException("already terminated");
            }
        }
    }

    public boolean terminated() {
        return terminated.get();
    }
}
