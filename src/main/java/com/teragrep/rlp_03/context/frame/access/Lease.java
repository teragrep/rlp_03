package com.teragrep.rlp_03.context.frame.access;

public class Lease implements AutoCloseable {

    private final Access access;

    private volatile boolean isOpen;
    Lease(Access access) {
        this.access = access;
        this.isOpen = true;
    }


    @Override
    public void close() {
        if (!isOpen) {
            throw new IllegalStateException();
        }
        else {
            isOpen = false;
            access.release(this);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }
}
