package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.frame.RelpFrame;

public class FrameContext {
    private final ConnectionContext connectionContext;
    private final RelpFrame relpFrame;
    public FrameContext(ConnectionContext connectionContext, RelpFrame relpFrame) {
        this.connectionContext = connectionContext;
        this.relpFrame = relpFrame;
    }

    public ConnectionContext connectionContext() {
        return connectionContext;
    }

    public RelpFrame relpFrame() {
        return relpFrame;
    }
}
