package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;

public class BufferLeaseStub implements BufferLease {
    @Override
    public long id() {
        throw new IllegalStateException("BufferLeaseStub does not have an id");
    }

    @Override
    public long refs() {
        throw new IllegalStateException("BufferLeaseStub does not have a refCount");
    }

    @Override
    public ByteBuffer buffer() {
        throw new IllegalStateException("BufferLeaseStub does not allow access to buffer");
    }

    @Override
    public void addRef() {
        throw new IllegalStateException("BufferLeaseStub does not allow addRef");
    }

    @Override
    public void removeRef() {
        throw new IllegalStateException("BufferLeaseStub does not allow removeRef");
    }

    @Override
    public boolean isRefCountZero() {
        throw new IllegalStateException("BufferLeaseStub does not provide isRefCountZero");
    }

    @Override
    public boolean isStub() {
        return true;
    }

    @Override
    public boolean attemptRelease() {
        throw new IllegalStateException("BufferLeaseStub does not provide attemptRelease");
    }
}
