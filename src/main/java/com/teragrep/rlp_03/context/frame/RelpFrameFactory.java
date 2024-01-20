package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_01.TxID;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentImpl;
import com.teragrep.rlp_03.context.frame.function.*;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RelpFrameFactory {

    private final int maximumFrameSize = 256*1024;
    private final int maximumCommandLength = 11;

    private final AtomicBoolean isComplete;

    RelpFrameFactory() {
        this.isComplete = new AtomicBoolean();
    }

    RelpFrame submit(ByteBuffer input) {

    }

    private static RelpFrame createFrame() {
        final int maximumFrameSize = 256*1024;
        final int maximumCommandLength = 11;

        Fragment txn = new FragmentImpl(String.valueOf(TxID.MAX_ID).length(),new TransactionFunction());
        Fragment command = new FragmentImpl(maximumCommandLength,new CommandFunction()); // TODO allow custom commands, sizeOf such too
        Fragment payloadLength = new FragmentImpl(String.valueOf(Integer.MAX_VALUE).length(),new PayloadLengthFunction());
        Fragment payload = new FragmentImpl(maximumFrameSize, new PayloadFunction(1)); // FIXME size
        Fragment endOfTransfer = new FragmentImpl(1, new EndOfTransferFunction());

        return new RelpFrame(txn, command, payloadLength, payload, endOfTransfer);
    }
}
