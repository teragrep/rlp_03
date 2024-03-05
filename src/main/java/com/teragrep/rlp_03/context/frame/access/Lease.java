package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.Phaser;

public class Lease implements AutoCloseable {

    private final Access access;
    private final Phaser subPhaser;
    Lease(Access access) {
        this.access = access;
        this.subPhaser = new Phaser(1);
    }


    @Override
    public void close() {
        if (subPhaser.isTerminated()) {
            throw new IllegalStateException("Lease phaser was terminated, can't close");
        }
        else {
            subPhaser.arriveAndDeregister(); // should terminate
            access.release(this);
        }
    }

    public boolean isTerminated() {
        return subPhaser.isTerminated();
    }
}
