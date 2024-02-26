package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
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
        ByteBuffer slice = input.slice();
        if (byteCount.get() + ((ByteBuffer) slice).limit() <= payloadLength) {
            // LOGGER.info("adding whole buffer byteCount.get() <{}> input.limit() <{}>", byteCount.get(), input.limit());
            // whole buffer is part of this payload
            byteCount.addAndGet(((ByteBuffer) slice).limit());
            input.position(input.limit()); // consume all
            // LOGGER.info("total byte count after adding whole buffer <{}>", byteCount.get());
        }
        else {
            // LOGGER.info("adding partial buffer byteCount.get() <{}> input.limit() <{}>", byteCount.get(), input.limit());
            int size = payloadLength - byteCount.get();
            ((ByteBuffer) slice).limit(size);
            input.position(input.position() + size); // consume rest of the payload
            byteCount.addAndGet(size);
            // LOGGER.info("created bufferSlice <{}>", bufferSlice);
        }

        bufferSliceList.add(slice);

        // LOGGER.info("return <{}> because byteCount.get() <{}> payloadLength <{}>", byteCount.get() == payloadLength, byteCount.get(), payloadLength);
        return byteCount.get() == payloadLength;
    }
}
