package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class FragmentImpl implements Fragment {
    private final LinkedList<ByteBuffer> bufferSliceList;

    // BiFunction is the parser function that takes: input, storageList, return value
    private final BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> parseRule;

    private final AtomicBoolean isComplete;

    public FragmentImpl(BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> parseRule) {
        this.bufferSliceList = new LinkedList<>();
        this.parseRule = parseRule;

        this.isComplete = new AtomicBoolean();
    }

    @Override
    public void accept(ByteBuffer input) {
        if (isComplete.get()) {
            throw new IllegalStateException("Fragment is complete, can not accept more.");
        }

        if (parseRule.apply(input, bufferSliceList)) { // TODO change to buffers and scatter gather pattern?
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

    @Override
    public byte[] toBytes() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }

        int totalBytes = 0;
        for (ByteBuffer slice : bufferSliceList) {
            totalBytes = totalBytes + slice.remaining();
        }
        byte[] bytes = new byte[totalBytes];

        int copiedBytes = 0;
        for (ByteBuffer slice : bufferSliceList) {
            int remainingBytes = slice.remaining();
            slice.asReadOnlyBuffer().get(bytes, copiedBytes, remainingBytes);
            copiedBytes = copiedBytes + remainingBytes;
        }

        return bytes;
    }

    @Override
    public String toString() {
        byte[] bytes = toBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    @Override
    public int toInt() {
        String integerString = toString();
        return Integer.parseInt(integerString);
    }

    @Override
    public FragmentWrite toFragmentWrite() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        LinkedList<ByteBuffer> bufferCopies = new LinkedList<>();
        for (ByteBuffer buffer : bufferSliceList) {
            bufferCopies.add(buffer.asReadOnlyBuffer());
        }
        return new FragmentWriteImpl(bufferCopies);
    }

    @Override
    public FragmentByteStream toFragmentByteStream() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        LinkedList<ByteBuffer> bufferCopies = new LinkedList<>();
        for (ByteBuffer buffer : bufferSliceList) {
            bufferCopies.add(buffer.asReadOnlyBuffer());
        }
        return new FragmentByteStreamImpl(bufferCopies);
    }



    @Override
    public long size() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + ((ByteBuffer) slice).limit();
        }
        return currentLength;
    }
}
