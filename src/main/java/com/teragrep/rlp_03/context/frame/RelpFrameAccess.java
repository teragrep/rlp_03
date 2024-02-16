package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentAccess;

public class RelpFrameAccess implements RelpFrame {

    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private final Fragment payload;
    private final Fragment endOfTransfer;
    private final boolean isStub;

    private final Access access;

    public RelpFrameAccess(RelpFrame relpFrame) {
        this.access = new Access();
        this.txn = new FragmentAccess(relpFrame.txn(), access);
        this.command = new FragmentAccess(relpFrame.command(), access);
        this.payloadLength = new FragmentAccess(relpFrame.payloadLength(), access);
        this.payload = new FragmentAccess(relpFrame.payload(), access);
        this.endOfTransfer = new FragmentAccess(relpFrame.endOfTransfer(), access);
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

    public Access access() {
        return access;
    }
}
