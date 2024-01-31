package com.teragrep.rlp_03.context.frame.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class TransactionFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    // private static final Logger LOGGER = LoggerFactory.getLogger(TransactionFunction.class);

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
            // LOGGER.info("read byte b <{}>", new String(new byte[]{b}, StandardCharsets.UTF_8));
            if (b == ' ') {
                rv = true;
                break;
            }
        }

        ByteBuffer bufferSlice;
        if (rv) {
            // adjust limit so that bufferSlice contains only this data, without the terminating ' '
            bufferSlice = (ByteBuffer) input.duplicate().limit(input.position() - 1);

        } else {
            bufferSlice = input.duplicate();
        }
        bufferSlice.rewind();
        bufferSliceList.add(bufferSlice);

        // LOGGER.info("bufferSliceList.size() <{}>", bufferSliceList.size());
        // LOGGER.info("TXN exiting with input <{}>", input);
        return rv;
    }
}
