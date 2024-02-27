package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BufferLeaseImpl implements BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLease.class);
    private final long id;
    private final ByteBuffer buffer;
    private final Lock lock;

    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.lock = new ReentrantLock();
    }

    @Override
    public long id() {
        return id;
    }

    /**
     * Unreliable, only for debug logging
     */
    @Override
    public long refs() {
        throw new IllegalStateException("refs not supported");
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
        throw new IllegalStateException("not supported on BufferLeaseImpl");
    }

    @Override
    public void removeRef() {
        throw new IllegalStateException("not supported on BufferLeaseImpl");

    }

    @Override
    public boolean isRefCountZero() {
        throw new IllegalStateException("not supported on BufferLeaseImpl");
    }


    @Override
    public String toString() {
        return "BufferLease{" +
                "buffer=" + buffer +
                ", refCount=" + refs() +
                '}';
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }

    @Override
    public boolean attemptRelease() {
        throw new IllegalStateException("not supported on BufferLeaseImpl");
    }

    @Override
    public boolean terminated() {
        throw new IllegalStateException("not supported on BufferLeaseImpl");
    }
}
