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
        if (phaser.isTerminated()) {
            // phaser was already closed by releasing all leases
            throw new IllegalStateException("Access phaser already terminated");
        }

        phaser.register(); // register new lease to phaser
        return new Lease(this);
    }

    public void terminate() throws IllegalStateException {
        phaser.arriveAndDeregister(); // registered=0, should terminate phaser
        if (!phaser.isTerminated()) {
            throw new IllegalStateException("Open leases exist!");
        }
    }

    public void release(Lease lease) {
        if (!lease.isTerminated()) {
            // don't allow releasing an open lease
            throw new IllegalStateException("Cannot release an open lease");
        } else if (phaser.isTerminated()) {
            throw new IllegalStateException("Phaser was already terminated");
        }
        // deregister released lease
        phaser.arriveAndDeregister();
    }

    public boolean isTerminated() {
        return phaser.isTerminated();
    }
}
