package com.teragrep.rlp_03.delegate.relp;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;

import java.util.ArrayList;
import java.util.List;

class RelpEventServerClose extends RelpEvent {
    @Override
    public void accept(FrameContext frameContext) {
        try {
            List<RelpFrameTX> txFrameList = new ArrayList<>();

            txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.SERVER_CLOSE, ""));

            frameContext.connectionContext().relpWrite().accept(txFrameList);
        } finally {
            frameContext.relpFrame().close();
        }
    }
}
