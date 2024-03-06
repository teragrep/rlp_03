package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;

/**
 * Buffer container stub object. Use isStub() to check.
 * Other methods will result in an IllegalStateException.
 */
public class BufferContainerStub implements BufferContainer {
    @Override
    public long id() {
        throw new IllegalStateException("BufferContainerStub does not have an id!");
    }

    @Override
    public ByteBuffer buffer() {
        throw new IllegalStateException("BufferContainerStub does not allow access to the buffer!");
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
