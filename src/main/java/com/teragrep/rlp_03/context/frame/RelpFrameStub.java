package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.fragment.Fragment;

public class RelpFrameStub implements RelpFrame {
    @Override
    public Fragment txn() {
        throw new IllegalStateException("RelpFrameStub does not allow this method");
    }

    @Override
    public Fragment command() {
        throw new IllegalStateException("RelpFrameStub does not allow this method");
    }

    @Override
    public Fragment payloadLength() {
        throw new IllegalStateException("RelpFrameStub does not allow this method");
    }

    @Override
    public Fragment payload() {
        throw new IllegalStateException("RelpFrameStub does not allow this method");
    }

    @Override
    public Fragment endOfTransfer() {
        throw new IllegalStateException("RelpFrameStub does not allow this method");
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
