package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

public class TransactionFunction implements BiFunction<ByteBuffer, ByteBuffer, Boolean> {
    @Override
    public Boolean apply(ByteBuffer input, ByteBuffer buffer) {
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();

            if (b == ' ') {
                rv = true;
            }
            else {
                buffer.put(b);
            }
        }
        return rv;
    }
}
