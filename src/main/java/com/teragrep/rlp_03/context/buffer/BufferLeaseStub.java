package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;

public class BufferLeaseStub implements BufferLease {
    @Override
    public long id() {
        throw new IllegalStateException("BufferLeaseStub does not have an id!");
    }

    @Override
    public long refs() {
        throw new IllegalStateException("BufferLeaseStub does not have refs!");
    }

    @Override
    public ByteBuffer buffer() {
        throw new IllegalStateException("BufferLeaseStub does not have a buffer!");
    }

    @Override
    public void addRef() {
        throw new IllegalStateException("BufferLeaseStub does not allow adding refs!");
    }

    @Override
    public void removeRef() {
        throw new IllegalStateException("BufferLeaseStub does not allow removing refs!");
    }

    @Override
    public boolean isRefCountZero() {
        throw new IllegalStateException("BufferLeaseStub does not have ref count!");
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
