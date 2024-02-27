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
            throw new IllegalStateException("lease phaser was terminated, can't close");
        }
        else if (isOpen()) {
            subPhaser.arriveAndDeregister(); // should terminate
            access.release(this);
        } else {
            throw new IllegalStateException("lease not open, can't close");
        }
    }

    public boolean isOpen() {
        return !subPhaser.isTerminated();
    }
}
