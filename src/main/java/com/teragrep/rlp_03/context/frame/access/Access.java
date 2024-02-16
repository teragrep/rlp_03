package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class Access implements Supplier<Lease> {

    private long accessCount;
    private boolean terminated;
    private final Lock lock;
    public Access() {
        this.accessCount = 0; // TODO consider using a semaphore
        this.terminated = false;
        this.lock = new ReentrantLock();
    }

    @Override
    public Lease get() {
        lock.lock();
        try {
            if (terminated()) {
                throw new IllegalStateException("Access already terminated");
            }

            accessCount++;
            return new Lease(this);
        } finally {
            lock.unlock();
        }
    }

    public void terminate() {
        if (lock.tryLock()) {
            try {
                if (accessCount != 0) {
                    throw new IllegalStateException("Open leases still exist");
                } else {
                    if (terminated) {
                        throw new IllegalStateException("Access already terminated");
                    }
                    terminated = true;
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
        lock.lock();
        try {
            return terminated;
        }
        finally {
            lock.unlock();
        }
    }

    public void release(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Can not be release an open lease");
        }
        lock.lock();
        try {
            long newAccessCount = accessCount - 1;
            if (newAccessCount < 0) {
                throw new IllegalStateException("AccessCount must not be negative");
            }
            accessCount = newAccessCount;
        }
        finally {
            lock.unlock();
        }
    }
}
