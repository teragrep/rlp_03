package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

public class PhaserDecoratedBufferLease implements BufferLease {
    protected BufferLease bufferLease;
    private final Phaser phaser;

    public PhaserDecoratedBufferLease(BufferLease bl) {
        this.bufferLease = bl;
        this.phaser = new Phaser(1); // registered = 1
    }

    @Override
    public long id() {
        return this.bufferLease.id();
    }

    @Override
    public long refs() {
        // initial number of registered parties is 1
        return phaser.getRegisteredParties();
    }

    @Override
    public ByteBuffer buffer() {
        return this.bufferLease.buffer();
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
        return this.bufferLease.isStub();
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

    @Override
    public boolean terminated() {
        return this.phaser.isTerminated();
    }

    @Override
    public boolean isPhaserDecorated() {
        return true;
    }
}
