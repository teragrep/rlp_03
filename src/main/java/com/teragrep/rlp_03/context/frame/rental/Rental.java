package com.teragrep.rlp_03.context.frame.rental;

import java.util.concurrent.Phaser;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Rental implements AutoCloseable, Consumer<Lease>, Supplier<Lease> {

    private final Phaser phaser;
    private boolean closed;
    public Rental() {
        this.phaser = new Phaser(1);
        this.closed = false;
    }

    @Override
    public Lease get() {
        if (closed()) {
            throw new IllegalStateException("Rental already terminated");
        }

        phaser.register();
        return new Lease(this);
    }

    @Override
    public void close() throws IllegalStateException {
        phaser.arriveAndDeregister();
        if (!phaser.isTerminated()) {
            throw new IllegalStateException("Open leases still exist");
        } else {
            if (closed) {
                throw new IllegalStateException("Rental already closed");
            }
            closed = true;
        }
    }

    @Override
    public void accept(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Cannot release an open lease");
        } else if (phaser.getRegisteredParties() <= 1) {
            throw new IllegalStateException("Registered parties must be more than one.");
        }
        phaser.arriveAndDeregister();
    }

    public boolean closed() {
        return closed;
    }
}
