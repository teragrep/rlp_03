package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class EndOfTransferFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {
    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
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

        ByteBuffer bufferSlice;
        if (rv) {
            // adjust limit so that bufferSlice contains only this data (\n)
            bufferSlice = (ByteBuffer) input.duplicate().limit(input.position());

        }
        else {
            bufferSlice = input;
        }
        bufferSlice.rewind();
        bufferSliceList.add(bufferSlice);

        return rv;
    }
}
