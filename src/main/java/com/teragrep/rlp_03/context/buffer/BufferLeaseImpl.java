package com.teragrep.rlp_03.context.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

public class BufferLeaseImpl implements BufferLease {
    private final BufferContainer bufferContainer;
    private final Phaser phaser;
    private final BufferLeasePool bufferLeasePool;

    public BufferLeaseImpl(BufferContainer bc, BufferLeasePool bufferLeasePool) {
        this.bufferContainer = bc;
        this.bufferLeasePool = bufferLeasePool;

        // initial registered parties set to 1
        this.phaser = new ClearingPhaser(1);
    }

    @Override
    public BufferContainer bufferContainer() {
        return bufferContainer;
    }

    @Override
    public long id() {
        return bufferContainer.id();
    }

    @Override
    public long refs() {
        // initial number of registered parties is 1
        return phaser.getRegisteredParties();
    }

    @Override
    public ByteBuffer buffer() {
        return bufferContainer.buffer();
    }

    @Override
    public void addRef() {
        if (phaser.register() < 0) {
            throw new IllegalStateException("Cannot add reference, BufferLease phaser was already terminated!");
        }
    }

    @Override
    public void removeRef() {
        if (phaser.arriveAndDeregister() < 0) {
            throw new IllegalStateException("Cannot remove reference, BufferLease phaser was already terminated!");
        }
    }

    @Override
    public boolean isRefCountZero() {
        return phaser.isTerminated();
    }

    @Override
    public boolean isStub() {
        return bufferContainer.isStub();
    }

    /**
     * Phaser that clears the buffer on termination (registeredParties=0)
     */
    private class ClearingPhaser extends Phaser {
        public ClearingPhaser(int i) {
            super(i);
        }

        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            boolean rv = false;
            if (registeredParties == 0) {
                buffer().clear();
                bufferLeasePool.internalOffer(bufferContainer);
                rv = true;
            }
            return rv;
        }
    }

}