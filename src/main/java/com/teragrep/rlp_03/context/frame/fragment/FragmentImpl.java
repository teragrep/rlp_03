package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public class FragmentImpl implements Fragment {

    //private static final Logger LOGGER = LoggerFactory.getLogger(FragmentImpl.class);

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
        // LOGGER.info("accept input<{}> with bufferSliceList.size() <{}>", input, bufferSliceList.size());

        if (isComplete.get()) {
            throw new IllegalStateException("Fragment is complete, can not accept more.");
        }

        if (parseRule.apply(input, bufferSliceList)) { // TODO change to buffers and scatter gather pattern?
            isComplete.set(true);
            //LOGGER.info("isComplete.get() <{}>", isComplete.get());
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
        //LOGGER.info("called toBytes");
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }

        int totalBytes = 0;
        // LOGGER.info("concatenating from bufferSliceList.size <{}>", bufferSliceList.size());
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
        //LOGGER.info("BYTES! parseRule <{}> returning bytes <{}>", parseRule, new String(bytes, StandardCharsets.UTF_8));
        return bytes;

    }

    @Override
    public String toString() {
        byte[] bytes = toBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    @Override
    public int toInt() {
        byte[] bytes = toBytes();
        // initialize UTF-8 decoder
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            // decode utf-8 byte representation
            CharBuffer charBuf = decoder.decode(ByteBuffer.wrap(bytes));
            // convert char[] to int
            return charArrayToInt(charBuf.array());
        } catch (CharacterCodingException e) {
            throw new RuntimeException("Character decoding error on toInt call: ", e);
        }
    }

    /**
     * Converts a char[] array to an int
     * @param arr Character array containing chars for int
     * @return integer
     */
    private int charArrayToInt(char[] arr) {
        int rv = 0;

        for (char c : arr) {
            int digit = c - '0';
            rv *= 10;
            rv += digit;
        }

        return rv;
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
