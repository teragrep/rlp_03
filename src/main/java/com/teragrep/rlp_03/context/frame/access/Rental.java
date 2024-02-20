package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.Phaser;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Rental implements AutoCloseable, Consumer<Lease>, Supplier<Lease> {

    private final Phaser phaser;
    private boolean terminated;
    public Rental() {
        this.phaser = new Phaser(1);
        this.terminated = false;
    }

    @Override
    public Lease get() {
        if (terminated()) {
            throw new IllegalStateException("Access already terminated");
        }

        phaser.register();
        return new Lease(this);
    }

    @Override
    public void close() /*throws Exception*/ {
        phaser.arriveAndDeregister();
        if (!phaser.isTerminated()) {
            throw new IllegalStateException("Open leases still exist");
        } else {
            if (terminated) {
                throw new IllegalStateException("Rental already closed");
            }
            terminated = true;
        }
    }

    @Override
    public void accept(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Cannot release an open lease");
        }
        phaser.arriveAndDeregister();
    }

    public boolean terminated() {
        return terminated;
    }
}
