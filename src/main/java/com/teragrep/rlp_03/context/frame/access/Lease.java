package com.teragrep.rlp_03.context.frame.access;

public class Lease implements AutoCloseable {

    private final Rental rental;

    private volatile boolean isOpen;
    Lease(Rental rental) {
        this.rental = rental;
        this.isOpen = true;
    }


    @Override
    public void close() {
        if (!isOpen) {
            throw new IllegalStateException();
        }
        else {
            isOpen = false;
            rental.accept(this);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }
}
