package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BufferLeaseImpl implements BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLeaseImpl.class);
    private final long id;
    private final ByteBuffer buffer;
    private long refCount; // TODO consider using a semaphore
    private final Lock lock;

    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.refCount = 0;
        this.lock = new ReentrantLock();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public long refs() {
        lock.lock();
        try {
            return refCount;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public ByteBuffer buffer() {
        lock.lock();
        try {
            return buffer;
        }
        finally {
            lock.unlock();
        }

    }

    @Override
    public void addRef() {
        lock.lock();
        try {
            refCount++;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void removeRef() {
        lock.lock();
        try {

            long newRefs = refCount - 1;
            if (newRefs < 0) {
                throw new IllegalStateException("refs must not be negative");
            }

            refCount = newRefs;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRefCountZero() {
        lock.lock();
        try {
            return refCount == 0;
        }
        finally {
            lock.unlock();
        }
    }


    @Override
    public String toString() {
        lock.lock();
        try {
            return "BufferLease{" +
                    "buffer=" + buffer +
                    ", refCount=" + refCount +
                    '}';
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }

    @Override
    public boolean attemptRelease() {
        lock.lock();
        try {
            boolean rv = false;
            removeRef();
            if (isRefCountZero()) {
                buffer().clear();
                // LOGGER.info("released bufferLease id <{}>, refs <{}>", bufferLease.id(), bufferLease.refs());
                rv = true;
            }
            return rv;

        } finally {
            lock.unlock();
        }
    }
}
