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
        try {
            if (terminated()) {
                throw new IllegalStateException("Access already terminated");
            }

            phaser.register();
            return new Lease(this);
        } finally {
           phaser.arriveAndDeregister();
        }
    }

    public void terminate() {
        phaser.arriveAndAwaitAdvance();
        try {
            if (phaser.getArrivedParties()!=0) {
                throw new IllegalStateException("Open leases still exist");
            } else {
                if (terminated) {
                    throw new IllegalStateException("Access already terminated");
                }
                terminated = true;
            }
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    public boolean terminated() {
        phaser.arriveAndAwaitAdvance();
        try {
            return terminated;
        }
        finally {
            phaser.arriveAndDeregister();
        }
    }

    public void release(Lease lease) {
        if (lease.isOpen()) {
            throw new IllegalStateException("Can not be release an open lease");
        }
        phaser.arriveAndAwaitAdvance();
        if (!phaser.isTerminated()) {
            throw new IllegalStateException("Phaser was not terminated!");
        }
        phaser.arriveAndDeregister();
    }
}
