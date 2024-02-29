package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

public class BufferLeaseImpl implements BufferLease {
    protected BufferContainer bufferContainer;
    private final Phaser phaser;

    public BufferLeaseImpl(BufferContainer bc) {
        this.bufferContainer = bc;
        this.phaser = new Phaser(1); // registered = 1
    }

    @Override
    public BufferContainer bufferContainer() {
        return this.bufferContainer;
    }

    @Override
    public long id() {
        return this.bufferContainer.id();
    }

    @Override
    public long refs() {
        // initial number of registered parties is 1
        return phaser.getRegisteredParties();
    }

    @Override
    public ByteBuffer buffer() {
        return this.bufferContainer.buffer();
    }

    @Override
    public void addRef() {
        this.phaser.register();
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
    public boolean isStub() {
        return this.bufferContainer.isStub();
    }

    @Override
    public boolean attemptRelease() {
        boolean rv = false;
        removeRef();
        if (isRefCountZero()) {
            buffer().clear();
            rv = true;
        }
        return rv;
    }

}
