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
    private final Phaser phaser;
    private final Lock lock;

    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.phaser = new Phaser(1); // registered=1
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
        // initial number of registered parties is 1
        return phaser.getRegisteredParties() - 1;
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
        phaser.register();
    }

    @Override
    public void removeRef() {
        phaser.arriveAndDeregister();
    }

    @Override
    public boolean isRefCountZero() {
        return phaser.isTerminated();
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
        boolean rv = false;
        removeRef();
        if (isRefCountZero()) {
            buffer().clear();
            // LOGGER.info("released bufferLease id <{}>, refs <{}>", bufferLease.id(), bufferLease.refs());
            rv = true;
        }
        return rv;
    }
}
