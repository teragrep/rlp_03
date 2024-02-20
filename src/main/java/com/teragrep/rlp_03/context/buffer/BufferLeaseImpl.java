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
    //private long refCount; // TODO consider using a semaphore
    private final Lock lock;

    // FIXME: remove await/deregister
    public BufferLeaseImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
        this.phaser = new Phaser(1);
        //this.refCount = 0;
        this.lock = new ReentrantLock();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public long refs() {
        //phaser.arriveAndAwaitAdvance();
        try {
            return phaser.getRegisteredParties();
        }
        finally {
            //phaser.arriveAndDeregister();
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
        //phaser.arriveAndAwaitAdvance();
        try {
            phaser.register();
        }
        finally {
            //phaser.arriveAndDeregister();
        }
    }

    @Override
    public void removeRef() {
        //phaser.arriveAndAwaitAdvance();
        try {
            phaser.arriveAndDeregister();
        }
        finally {
            //phaser.arriveAndDeregister();
        }
    }

    @Override
    public boolean isRefCountZero() {
        //phaser.arriveAndAwaitAdvance();
        try {
            return phaser.isTerminated();
        }
        finally {
            //phaser.arriveAndDeregister();
        }
    }


    @Override
    public String toString() {
        //phaser.arriveAndAwaitAdvance();
        try {
            return "BufferLease{" +
                    "buffer=" + buffer +
                    ", refCount=" + phaser.getRegisteredParties() +
                    '}';
        } finally {
            //phaser.arriveAndDeregister();
        }
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }

    @Override
    public boolean attemptRelease() {
        //phaser.arriveAndAwaitAdvance();
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
            //phaser.arriveAndDeregister();
        }
    }
}
