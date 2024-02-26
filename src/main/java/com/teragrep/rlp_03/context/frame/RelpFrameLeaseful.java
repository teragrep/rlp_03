package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.buffer.BufferLease;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;

import java.util.LinkedList;
import java.util.List;

public class RelpFrameLeaseful implements RelpFrame {

    private final RelpFrameImpl relpFrame;

    private final List<BufferLease> leases;

    public RelpFrameLeaseful(RelpFrameImpl relpFrame) {
        this.relpFrame = relpFrame;
        this.leases = new LinkedList<>();
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
        leases.add(bufferLease);
        return relpFrame.submit(bufferLease.buffer());
    }

    public List<BufferLease> release() {
        return leases;
    }

    @Override
    public String toString() {
        return "RelpFrameLeaseful{" +
                "relpFrame=" + relpFrame +
                ", leaseSet=" + "REMOVED" +
                '}';
    }
}
