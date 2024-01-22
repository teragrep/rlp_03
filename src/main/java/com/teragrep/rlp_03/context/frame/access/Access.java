package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class Access implements Supplier<Lease> {

    private final AtomicLong accessCount;
    private final AtomicBoolean terminated;
    private final Lock lock;
    public Access() {
        this.accessCount = new AtomicLong();
        this.terminated = new AtomicBoolean();
        this.lock = new ReentrantLock();
    }

    @Override
    public Lease get() {
        lock.lock();
        try {
            if (terminated()) {
                throw new IllegalStateException("Access already terminated");
            }

            accessCount.incrementAndGet();
            return new Lease(this);
        } finally {
            lock.unlock();
        }
    }

    public void terminate() {
        if (lock.tryLock()) {
            try {
                if (accessCount.get() != 0) {
                    throw new IllegalStateException("Open leases still exist");
                } else {
                    if (!terminated.compareAndSet(false, true)) {
                        throw new IllegalStateException("Access already terminated");
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        else {
            throw new IllegalStateException("Lease operation in progress");
        }
    }

    public boolean terminated() {
        return terminated.get();
    }

    public void release(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Can not be release an open lease");
        }
        lock.lock();
        try {
            accessCount.decrementAndGet();
        }
        finally {
            lock.unlock();
        }
    }
}
