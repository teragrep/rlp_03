package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentStub;

public class RelpFrame {
    public final Fragment txn;
    public final Fragment command;
    public final Fragment payloadLength;
    public final Fragment payload;
    public final Fragment endOfTransfer;

    public final boolean isStub;

    RelpFrame() {
        this(
                new FragmentStub(),
                new FragmentStub(),
                new FragmentStub(),
                new FragmentStub(),
                new FragmentStub(),
                true
        );
    }
    public RelpFrame(Fragment txn, Fragment command, Fragment payloadLength, Fragment payload, Fragment endOfTransfer) {
        this(txn, command, payloadLength, payload, endOfTransfer, false);
    }
    RelpFrame(Fragment txn, Fragment command, Fragment payloadLength, Fragment payload, Fragment endOfTransfer, boolean isStub) {
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
}
