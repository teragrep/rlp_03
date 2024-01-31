package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentStub;

public class RelpFrameImpl implements RelpFrame {
    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private final Fragment payload;
    private final Fragment endOfTransfer;

    private final boolean isStub;

    public RelpFrameImpl(Fragment txn, Fragment command, Fragment payloadLength, Fragment payload, Fragment endOfTransfer) {
        this(txn, command, payloadLength, payload, endOfTransfer, false);
    }
    RelpFrameImpl(Fragment txn, Fragment command, Fragment payloadLength, Fragment payload, Fragment endOfTransfer, boolean isStub) {
        this.txn = txn;
        this.command = command;
        this.payloadLength = payloadLength;
        this.payload = payload;
        this.endOfTransfer = endOfTransfer;
        this.isStub = isStub;
    }

    @Override
    public String toString() {
        return "RelpFrame{" +
                "txn=" + txn.toInt() +
                ", command=" + command.toString() +
                ", payloadLength=" + payloadLength.toInt() +
                ", payload=" + payload.toString() +
                ", endOfTransfer=" + endOfTransfer.toString() +
                ", isStub=" + isStub +
                '}';
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
}
