package com.teragrep.rlp_03.delegate.relp;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.context.frame.RelpFrame;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

abstract class RelpEvent implements Consumer<FrameContext>, AutoCloseable {
    protected RelpFrameTX createResponse(
            RelpFrame rxFrame,
            String command,
            String response) {
        RelpFrameTX txFrame = new RelpFrameTX(command, response.getBytes(StandardCharsets.UTF_8));
        txFrame.setTransactionNumber(rxFrame.txn().toInt());
        return txFrame;
    }

    @Override
    public void close() throws Exception {

    }
}
