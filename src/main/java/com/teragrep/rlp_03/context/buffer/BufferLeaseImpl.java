package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class BufferLeaseImpl implements BufferLease {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLeaseImpl.class);
    private final long id;
    private final ByteBuffer buffer;
    private final AtomicLong refCount;


    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.refCount = new AtomicLong();
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
    public synchronized ByteBuffer buffer() {
            return buffer;
    }

    @Override
    public void addRef() {
        refCount.getAndIncrement();
    }

    @Override
    public void removeRef() {
        long newRefs = refCount.decrementAndGet();
        if (newRefs < 0) {
            throw new IllegalStateException("refs must not be negative");
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
                ", refCount=" + refCount.get() +
                '}';
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }

    @Override
    public synchronized boolean attemptRelease() {
        boolean rv = false;
        removeRef();
        if (refCount.get() == 0) {
            buffer().clear();
            rv = true;
        }
        return rv;
    }
}
