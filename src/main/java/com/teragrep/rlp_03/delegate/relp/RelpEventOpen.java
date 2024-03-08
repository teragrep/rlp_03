package com.teragrep.rlp_03.delegate.relp;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;

import java.util.ArrayList;
import java.util.List;

class RelpEventOpen extends RelpEvent {

    private static final String responseData = "200 OK\nrelp_version=0\n"
            + "relp_software=RLP-01,1.0.1,https://teragrep.com\n"
            + "commands=" + RelpCommand.SYSLOG + "\n";

    @Override
    public void accept(FrameContext frameContext) {
        try {
            List<RelpFrameTX> txFrameList = new ArrayList<>();

            txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.RESPONSE, responseData));

            frameContext.connectionContext().relpWrite().accept(txFrameList);
        } finally {
            frameContext.relpFrame().close();
        }
    }
}
