package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.Phaser;
import java.util.function.Supplier;

public final class Access implements Supplier<Lease> {
    final Phaser phaser;
    public Access() {
        // initial registered parties is 1, same as calling phaser.register()
        this.phaser = new Phaser(1);
    }

    @Override
    public Lease get() {
        if (phaser.register() < 0) {
            // negative return value on phaser.register() means it was already terminated
            // phaser was already closed by releasing all leases
            throw new IllegalStateException("Access phaser already terminated");
        }

        return new Lease(this);
    }

    public void terminate() throws IllegalStateException {
        // non-negative return value means phaser is not terminated
        if (phaser.arriveAndDeregister() > 0) {
            throw new IllegalStateException("Open leases exist!");
        }
    }

    public void release(Lease lease) {
        if (!lease.isTerminated()) {
            // don't allow releasing an open lease
            throw new IllegalStateException("Cannot release an open lease");
        } else if (phaser.arriveAndDeregister() < 0) {
            // negative return value means phaser was already terminated
            throw new IllegalStateException("Phaser was already terminated");
        }
    }

    public boolean isTerminated() {
        return phaser.isTerminated();
    }
}
