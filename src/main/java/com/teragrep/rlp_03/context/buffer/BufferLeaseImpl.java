package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// FIXME create tests
public class BufferLeaseImpl implements BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLease.class);
    private final long id;
    private final ByteBuffer buffer;
    private final AtomicLong refCount;
    private final Lock lock;
    public BufferLeaseImpl(long id, ByteBuffer buffer){
        this.id = id;
        this.buffer = buffer;
        this.refCount = new AtomicLong();
        this.lock = new ReentrantLock();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public long refs() {
        return refCount.get();
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
            refCount.incrementAndGet();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void removeRef() {
        lock.lock();
        try {
            refCount.decrementAndGet();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRefCountZero() {
        return refCount.get() == 0;
    }


    @Override
    public String toString() {
        return "BufferLease{" +
                "buffer=" + buffer +
                ", refCount=" + refCount +
                '}';
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }
}
