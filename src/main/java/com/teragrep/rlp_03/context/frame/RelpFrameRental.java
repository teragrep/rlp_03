package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.rental.Rental;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentRental;

public class RelpFrameRental implements RelpFrame {

    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private final Fragment payload;
    private final Fragment endOfTransfer;
    private final boolean isStub;

    private final Rental rental;

    public RelpFrameRental(RelpFrame relpFrame) {
        this.rental = new Rental();
        this.txn = new FragmentRental(relpFrame.txn(), rental);
        this.command = new FragmentRental(relpFrame.command(), rental);
        this.payloadLength = new FragmentRental(relpFrame.payloadLength(), rental);
        this.payload = new FragmentRental(relpFrame.payload(), rental);
        this.endOfTransfer = new FragmentRental(relpFrame.endOfTransfer(), rental);
        this.isStub = relpFrame.isStub();
    }
    
    @Override
    public Fragment txn() {
        return txn;
    }

    @Override
    public Fragment command() {
        return command;
    }

    @Override
    public Fragment payloadLength() {
        return payloadLength;
    }

    @Override
    public Fragment payload() {
        return payload;
    }

    @Override
    public Fragment endOfTransfer() {
        return endOfTransfer;
    }

    @Override
    public boolean isStub() {
        return isStub;
    }

    @Override
    public String toString() {
        return "RelpFrameRental{" +
                "txn=" + txn +
                ", command=" + command +
                ", payloadLength=" + payloadLength +
                ", payload=" + payload +
                ", endOfTransfer=" + endOfTransfer +
                '}';
    }

    public Rental rental() {
        return rental;
    }
}
