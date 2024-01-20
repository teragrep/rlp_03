package com.teragrep.rlp_03.context.frame.fragment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class FragmentImpl implements Fragment {
    private final ByteBuffer buffer;

    // BiFunction is the parser function that takes: input, storage, return value
    private final BiFunction<ByteBuffer, ByteBuffer, Boolean> parseRule;

    private final AtomicBoolean isComplete;

    public FragmentImpl(int bufferSize, BiFunction<ByteBuffer, ByteBuffer, Boolean> parseRule) {
        this.buffer = ByteBuffer.allocateDirect(bufferSize);
        this.parseRule = parseRule;

        this.isComplete = new AtomicBoolean();
    }

    @Override
    public void accept(ByteBuffer input) {
        if (isComplete.get()) {
            throw new IllegalStateException("Fragment is complete, can not accept more.");
        }

        if (parseRule.apply(input, buffer)) { // TODO change to buffers and scatter gather pattern?
            isComplete.set(true);
        }
    }

    @Override
    public boolean isStub() {
        return false;
    }

    @Override
    public boolean isComplete() {
        return isComplete.get();
    }
}
