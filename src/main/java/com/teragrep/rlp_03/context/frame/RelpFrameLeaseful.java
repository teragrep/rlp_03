package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.buffer.BufferLease;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;

import java.util.HashSet;
import java.util.Set;

public class RelpFrameLeaseful implements RelpFrame {

    private final RelpFrameImpl relpFrame;

    private final Set<BufferLease> leaseSet;

    public RelpFrameLeaseful(RelpFrameImpl relpFrame) {
        this.relpFrame = relpFrame;
        this.leaseSet = new HashSet<>();
    }

    @Override
    public Fragment txn() {
        return relpFrame.txn();
    }

    @Override
    public Fragment command() {
        return relpFrame.command();
    }

    @Override
    public Fragment payloadLength() {
        return relpFrame.payloadLength();
    }

    @Override
    public Fragment payload() {
        return relpFrame.payload();
    }

    @Override
    public Fragment endOfTransfer() {
        return relpFrame.endOfTransfer();
    }

    @Override
    public boolean isStub() {
        return relpFrame.isStub();
    }

    public boolean submit(BufferLease bufferLease) {
        if (!leaseSet.contains(bufferLease)) {
            bufferLease.addRef();
            leaseSet.add(bufferLease);
        }
        return relpFrame.submit(bufferLease.buffer());
    }

    public Set<BufferLease> release() {
        return leaseSet;
    }
}
