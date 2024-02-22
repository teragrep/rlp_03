package com.teragrep.rlp_03.context.frame.rental;

import java.util.concurrent.Phaser;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Rental implements AutoCloseable, Consumer<Lease>, Supplier<Lease> {
    private final Phaser phaser;
    public Rental() {
        this.phaser = new Phaser(1);
    }

    @Override
    public Lease get() {
        if (phaser.isTerminated()) {
            // phaser was already closed by releasing all leases
            throw new IllegalStateException("Rental phaser already terminated");
        }

        phaser.register(); // register new lease to phaser
        return new Lease(this);
    }

    @Override
    public void close() throws IllegalStateException {
        // more than one registered party means there is an open (non-released) lease
        if (phaser.getRegisteredParties() > 1) {
            throw new IllegalStateException("Open leases still exist");
        }
        phaser.arriveAndDeregister(); // registered=0, should terminate phaser
    }

    @Override
    public void accept(Lease lease) {
        if (lease.isOpen()) {
            // don't allow releasing an open lease
            throw new IllegalStateException("Cannot release an open lease");
        }
        // deregister released lease
        phaser.arriveAndDeregister();
    }

    public boolean closed() {
        // should be closed if terminated
        return phaser.isTerminated();
    }
}
