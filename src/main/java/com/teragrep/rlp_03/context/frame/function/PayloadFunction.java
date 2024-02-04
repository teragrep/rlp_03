package com.teragrep.rlp_03.context.frame.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public class PayloadFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    // private static final Logger LOGGER = LoggerFactory.getLogger(PayloadFunction.class);

    final int payloadLength;

    final AtomicInteger byteCount;
    public PayloadFunction(int payloadLength) {
        this.payloadLength = payloadLength;
        this.byteCount = new AtomicInteger();
    }
    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {

        ByteBuffer bufferSlice;
        if (byteCount.get() + input.limit() <= payloadLength) {
            // LOGGER.info("adding whole buffer byteCount.get() <{}> input.limit() <{}>", byteCount.get(), input.limit());
            // whole buffer is part of this payload
            bufferSlice = (ByteBuffer) input.duplicate().rewind();
            byteCount.addAndGet(bufferSlice.limit());
            input.position(input.limit()); // consume all
            // LOGGER.info("total byte count after adding whole buffer <{}>", byteCount.get());
        }
        else {
            // LOGGER.info("adding partial buffer byteCount.get() <{}> input.limit() <{}>", byteCount.get(), input.limit());
            int size = payloadLength - byteCount.get();
            bufferSlice = (ByteBuffer) input.duplicate().limit(size);
            input.position(size);
            byteCount.addAndGet(size);
            // LOGGER.info("created bufferSlice <{}>", bufferSlice);
        }
        bufferSliceList.add(bufferSlice);

        // LOGGER.info("return <{}> because byteCount.get() <{}> payloadLength <{}>", byteCount.get() == payloadLength, byteCount.get(), payloadLength);
        return byteCount.get() == payloadLength;
    }
}
