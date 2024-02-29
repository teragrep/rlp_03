package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

public interface BufferLease {
    long id();

    long refs();
    ByteBuffer buffer();

    void addRef();

    void removeRef();

    boolean isRefCountZero();

    boolean isStub();

    boolean attemptRelease();

    boolean terminated();

    boolean isPhaserDecorated();
}
