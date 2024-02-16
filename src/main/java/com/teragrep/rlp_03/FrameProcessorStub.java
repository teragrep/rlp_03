package com.teragrep.rlp_03;


public class FrameProcessorStub implements FrameProcessor {

    @Override
    public void accept(FrameContext frameServerRX) {

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
