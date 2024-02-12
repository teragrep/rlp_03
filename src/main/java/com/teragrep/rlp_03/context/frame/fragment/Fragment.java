package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface Fragment extends Consumer<ByteBuffer> {
    @Override
    void accept(ByteBuffer input);

    boolean isStub();

    boolean isComplete();

    byte[] toBytes();
    String toString();
    int toInt();

    FragmentWrite toFragmentWrite();

    long size();
}
