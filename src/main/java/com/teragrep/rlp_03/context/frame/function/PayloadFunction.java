package com.teragrep.rlp_03.context.frame.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public class PayloadFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {
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
            // whole buffer is part of this payload
            byteCount.addAndGet(((ByteBuffer) slice).limit());
            input.position(input.limit()); // consume all
        }
        else {
            int size = payloadLength - byteCount.get();
            ((ByteBuffer) slice).limit(size);
            input.position(input.position() + size); // consume rest of the payload
            byteCount.addAndGet(size);
        }

        bufferSliceList.add(slice);

        return byteCount.get() == payloadLength;
    }
}
