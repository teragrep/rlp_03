package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;

public class FragmentStub implements Fragment {

    @Override
    public void accept(ByteBuffer input) {
        throw new IllegalStateException("FragmentStub can not accept");
    }

    @Override
    public boolean isStub() {
        return true;
    }

    @Override
    public boolean isComplete() {
        throw new IllegalStateException("FragmentStub can not be complete");
    }
}
