package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.Phaser;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class Access implements Supplier<Lease> {

    private final Phaser phaser;
    private boolean terminated;
    public Access() {
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

    public void terminate() {
        phaser.arriveAndDeregister();
        try {
            if (phaser.getRegisteredParties()!=0) {
                throw new IllegalStateException("Open leases still exist");
            } else {
                if (terminated) {
                    throw new IllegalStateException("Access already terminated");
                }
                terminated = true;
            }
        } finally {

        }
    }

    public boolean terminated() {
        return terminated;
    }

    public void release(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Can not be release an open lease");
        }
        phaser.arriveAndDeregister();
    }
}
