package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameProcessor;

import java.util.List;

public class FrameProcessorStub implements FrameProcessor {
    @Override
    public List<RelpFrameTX> process(RelpFrameServerRX frameServerRX) {
        throw new IllegalArgumentException("FrameProcessorStub can not process");
    }

    @Override
    public void close() throws Exception {
        throw new IllegalArgumentException("FrameProcessorStub can not close");
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
