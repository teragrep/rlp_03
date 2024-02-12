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

    @Override
    public byte[] toBytes() {
        return new byte[0];
    }

    @Override
    public String toString() {
        throw new IllegalStateException("FragmentStub can not resolve toString");
    }

    @Override
    public int toInt() {
        throw new IllegalStateException("FragmentStub can not resolve toInt");
    }

    @Override
    public FragmentWrite toFragmentWrite() {
        throw new IllegalStateException("FragmentStub can not resolve toFragmentWrite");
    }

    @Override
    public long size() {
        throw new IllegalStateException("FragmentStub can not resolve size");
    }


}
