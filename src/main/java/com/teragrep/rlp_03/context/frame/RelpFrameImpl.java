package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentImpl;
import com.teragrep.rlp_03.context.frame.fragment.FragmentStub;
import com.teragrep.rlp_03.context.frame.function.*;

import java.nio.ByteBuffer;


// TODO Design how to use Access properly if RelpFrames are also poolable
public class RelpFrameImpl implements RelpFrame {
    //private static final Logger LOGGER = LoggerFactory.getLogger(RelpFrameImpl.class);

    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private Fragment payload;
    private final Fragment endOfTransfer;

    public RelpFrameImpl() {
        this.txn = new FragmentImpl(new TransactionFunction());
        this.command = new FragmentImpl(new CommandFunction());
        this.payloadLength = new FragmentImpl(new PayloadLengthFunction());
        this.payload = new FragmentStub();
        this.endOfTransfer = new FragmentImpl(new EndOfTransferFunction());
    }

    public boolean submit(ByteBuffer input) {
        boolean rv = false;
        //LOGGER.info("submit for input <{}>", input);

        while (input.hasRemaining() && !rv) {
            //LOGGER.info("thisBuffer <{}>", input);

            if (!txn.isComplete()) {
                //LOGGER.info("accepting into TXN thisBuffer <{}>", input);
                txn.accept(input);
            } else if (!command.isComplete()) {
                //LOGGER.info("accepting into COMMAND thisBuffer <{}>", input);
                command.accept(input);
            } else if (!payloadLength.isComplete()) {
                //LOGGER.info("accepting into PAYLOAD LENGTH thisBuffer <{}>", input);

                payloadLength.accept(input);

                if (payloadLength.isComplete()) {
                    // PayloadFunction depends on payload length and needs to by dynamically created
                    int payloadSize = payloadLength.toInt();
                    //LOGGER.info("creating PayloadFunction with payloadSize <{}>", payloadSize);
                    payload = new FragmentImpl(new PayloadFunction(payloadSize));
                }
            } else if (!payload.isComplete()) {
                //LOGGER.info("accepting into PAYLOAD thisBuffer <{}>", input);

                payload.accept(input);
            } else if (!endOfTransfer.isComplete()) {
                //LOGGER.info("accepting into endOfTransfer");
                endOfTransfer.accept(input);

                if (endOfTransfer.isComplete()) {
                    // all complete
                    rv = true;
                }
            } else {
                throw new IllegalStateException("submit not allowed on a complete frame");
            }

            //LOGGER.info("after read input <{}>", input);
        }
        //LOGGER.info("returning rv <{}>", rv);
        return rv;
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
        return false;
    }

    @Override
    public String toString() {
        return "RelpFrameImpl{" +
                "txn=" + txn +
                ", command=" + command +
                ", payloadLength=" + payloadLength +
                ", payload=" + payload +
                ", endOfTransfer=" + endOfTransfer +
                '}';
    }
}
