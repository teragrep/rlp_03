package com.teragrep.rlp_03;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Status {

    private final Lock lock;
    private final Condition pending;
    private final AtomicBoolean done;



    Status() {
        this.lock = new ReentrantLock();
        this.pending = lock.newCondition();
        this.done = new AtomicBoolean();
    }

    void complete() {
        lock.lock();
        done.set(true);
        pending.signal();
        lock.unlock();
    }

    public void waitForCompletion() throws InterruptedException {
        boolean completion;
        while (true) {
            lock.lock();
            completion = done.get();
            if (completion) {
                break;
            }
            pending.await();
        }
    }
}
