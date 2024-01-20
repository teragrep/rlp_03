package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

public class PayloadFunction implements BiFunction<ByteBuffer, ByteBuffer, Boolean> {

    final int payloadLength;
    PayloadFunction(int payloadLength) {
        this.payloadLength = payloadLength;
    }
    @Override
    public Boolean apply(ByteBuffer input, ByteBuffer buffer) {
        boolean rv = false;
        while (input.hasRemaining() && buffer.position() < payloadLength) {
            // TODO optimize by getting a slice of input for buffer.put(ByteBuffer inputSlice);
            if (buffer.position() == buffer.capacity()) {
                throw new IllegalArgumentException("payload length exceeds payload size");
            }
            byte b = input.get();
            buffer.put(b);
        }
        if (buffer.position() == payloadLength) {
            rv = true;
        }
        return rv;
    }
}
