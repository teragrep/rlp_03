package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentAccess;

public class RelpFrameAccess implements RelpFrame {

    private final RelpFrame relpFrame;
    private final Access access;

    public RelpFrameAccess(RelpFrame relpFrame) {
        this.relpFrame = relpFrame;
        this.access = new Access();
    }
    
    @Override
    public Fragment txn() {
        return new FragmentAccess(relpFrame.txn(), access);
    }

    @Override
    public Fragment command() {
        return new FragmentAccess(relpFrame.command(), access);
    }

    @Override
    public Fragment payloadLength() {
        return new FragmentAccess(relpFrame.payloadLength(), access);
    }

    @Override
    public Fragment payload() {
        return new FragmentAccess(relpFrame.payload(), access);
    }

    @Override
    public Fragment endOfTransfer() {
        return new FragmentAccess(relpFrame.endOfTransfer(), access);
    }

    @Override
    public boolean isStub() {
        return relpFrame.isStub();
    }

    @Override
    public String toString() {
        return "RelpFrameImpl{" +
                "txn=" + txn() +
                ", command=" + command() +
                ", payloadLength=" + payloadLength() +
                ", payload=" + payload() +
                ", endOfTransfer=" + endOfTransfer() +
                '}';
    }

    public Access access() {
        return access;
    }
}
