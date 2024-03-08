package com.teragrep.rlp_03.delegate.relp;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class RelpEventClose extends RelpEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpEventClose.class);

    @Override
    public void accept(FrameContext frameContext) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("received close on txn <[{}]>", frameContext.relpFrame().txn().toString());
            }

            List<RelpFrameTX> txFrameList = new ArrayList<>();

            txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.RESPONSE, ""));
            // closure is immediate!
            txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.SERVER_CLOSE, ""));

            frameContext.connectionContext().relpWrite().accept(txFrameList);
        } finally {
            frameContext.relpFrame().close();
        }

    }
}
