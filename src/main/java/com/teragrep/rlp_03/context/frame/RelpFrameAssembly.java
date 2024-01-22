package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentImpl;
import com.teragrep.rlp_03.context.frame.function.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.LinkedList;

public class RelpFrameAssembly {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpFrameAssembly.class);

    private final RelpFrame relpFrameStub;

    // FIXME hackism just for testing it -v
    private final LinkedList<Fragment> fragments;

    private Access access = new Access();
    private Fragment currentFragment = new FragmentImpl(new TransactionFunction(), access);
    // FIXME hackism just for testing it -^

    RelpFrameAssembly() {
        this.relpFrameStub = new RelpFrame();

        this.fragments = new LinkedList<>();
    }

    // TODO use BufferLease
    RelpFrame submit(ByteBuffer input) {

        ByteBuffer thisBuffer = input;
        while (thisBuffer.hasRemaining()) {
            int fragmentCount = fragments.size();

            // LOGGER.info("switching fragmentCount <{}> with thisBuffer <{}>", fragmentCount, thisBuffer);
            switch (fragmentCount) {
                case 0:
                    // LOGGER.info("accepting into TXN");
                    currentFragment.accept(thisBuffer);
                    if (currentFragment.isComplete()) {
                        fragments.add(currentFragment);
                        currentFragment = new FragmentImpl(new CommandFunction(), access);
                    }
                    thisBuffer = thisBuffer.slice();
                    break;
                case 1:
                    // LOGGER.info("accepting into COMMAND");
                    currentFragment.accept(thisBuffer);
                    if (currentFragment.isComplete()) {
                        fragments.add(currentFragment);
                        currentFragment = new FragmentImpl(new PayloadLengthFunction(), access);
                    }
                    thisBuffer = thisBuffer.slice();
                    break;
                case 2:
                    // LOGGER.info("accepting into PAYLOAD LENGTH");
                    currentFragment.accept(thisBuffer);
                    if (currentFragment.isComplete()) {
                        fragments.add(currentFragment);

                        int payloadLength = currentFragment.toInt();
                        currentFragment = new FragmentImpl(new PayloadFunction(payloadLength), access);
                    }
                    thisBuffer = thisBuffer.slice();
                    break;
                case 3:
                    // LOGGER.info("accepting into PAYLOAD");
                    currentFragment.accept(thisBuffer);
                    if (currentFragment.isComplete()) {
                        fragments.add(currentFragment);
                        currentFragment = new FragmentImpl(new EndOfTransferFunction(), access);
                    }
                    thisBuffer = thisBuffer.slice();
                    break;
                case 4:
                    // LOGGER.info("accepting into ENDOFTRANSFER");
                    currentFragment.accept(thisBuffer);
                    if (currentFragment.isComplete()) {
                        fragments.add(currentFragment);
                        currentFragment = new FragmentImpl(new TransactionFunction(), access);
                    }
                    break;
                default:
                    throw new IllegalStateException("you should not be here"); // FIXME
            }
            // LOGGER.info("fragments.size() <{}>", fragments.size());
            if (fragments.size() == 5) {
                // frame complete
                // FIXME total hack here
                RelpFrame relpFrame = new RelpFrame(fragments.get(0), fragments.get(1), fragments.get(2), fragments.get(3), fragments.get(4));
                fragments.clear();
                return relpFrame;
            }
        }

        // LOGGER.info("returning relpFrameStub because thisBuffer <{}>", thisBuffer);
        return relpFrameStub;
    }

    public void free(RelpFrame relpFrame) {
        access.terminate();
    }
}
