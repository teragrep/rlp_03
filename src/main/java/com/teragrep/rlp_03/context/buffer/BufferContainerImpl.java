package com.teragrep.rlp_03.context.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Implementation of the BufferContainer interface. Contains the buffer with a synchronized (lock-free)
 * way of accessing it.
 */
public class BufferContainerImpl implements BufferContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferLease.class);
    private final long id;
    private final ByteBuffer buffer;

    public BufferContainerImpl(long id, ByteBuffer buffer) {
        this.id = id;
        this.buffer = buffer;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public synchronized ByteBuffer buffer() {
        return buffer;
    }

    @Override
    public String toString() {
        return "BufferLease{" +
                "buffer=" + buffer +
                '}';
    }

    @Override
    public boolean isStub() {
        LOGGER.debug("id <{}>", id);
        return false;
    }
}
