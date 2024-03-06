package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;

public interface BufferLease {
    long id();

    long refs();

    ByteBuffer buffer();

    void addRef();

    void removeRef();

    boolean isRefCountZero();

    boolean isStub();
}
