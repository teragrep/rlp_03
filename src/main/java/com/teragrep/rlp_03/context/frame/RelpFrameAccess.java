package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.access.Rental;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentAccess;

public class RelpFrameAccess implements RelpFrame {

    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private final Fragment payload;
    private final Fragment endOfTransfer;
    private final boolean isStub;

    private final Rental rental;

    public RelpFrameAccess(RelpFrame relpFrame) {
        this.rental = new Rental();
        this.txn = new FragmentAccess(relpFrame.txn(), rental);
        this.command = new FragmentAccess(relpFrame.command(), rental);
        this.payloadLength = new FragmentAccess(relpFrame.payloadLength(), rental);
        this.payload = new FragmentAccess(relpFrame.payload(), rental);
        this.endOfTransfer = new FragmentAccess(relpFrame.endOfTransfer(), rental);
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
        return "RelpFrameAccess{" +
                "txn=" + txn +
                ", command=" + command +
                ", payloadLength=" + payloadLength +
                ", payload=" + payload +
                ", endOfTransfer=" + endOfTransfer +
                '}';
    }

    public Rental access() {
        return rental;
    }
}
