package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

public class EndOfTransferFunction implements BiFunction<ByteBuffer, ByteBuffer, Boolean> {
    @Override
    public Boolean apply(ByteBuffer input, ByteBuffer buffer) {
        boolean rv = false;
        if (input.hasRemaining()) {
            byte b = input.get();
            if (b == '\n') {
                // RelpFrame always ends with a newline byte.
                rv = true;
            } else {
                throw new IllegalStateException("RelpFrame EndOfTransfer \\n missing");
            }
        }

        return rv;
    }
}
