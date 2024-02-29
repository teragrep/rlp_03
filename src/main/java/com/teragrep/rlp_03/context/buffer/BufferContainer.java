package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;

/**
 * Interface for a buffer container object.
 */
public interface BufferContainer {
    long id();
    ByteBuffer buffer();
    boolean isStub();
}
