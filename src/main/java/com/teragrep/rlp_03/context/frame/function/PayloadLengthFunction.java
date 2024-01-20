package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;

public class PayloadLengthFunction implements BiFunction<ByteBuffer, ByteBuffer, Boolean> {
    @Override
    public Boolean apply(ByteBuffer input, ByteBuffer buffer) {
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();

            if ( b == '\n') {
                /*
                 '\n' is especially for librelp which should follow:
                 HEADER = TXNR SP COMMAND SP DATALEN SP;
                 but sometimes librelp follows:
                 HEADER = TXNR SP COMMAND SP DATALEN LF; and LF is for EndOfTransfer
                 */
                // seek one byte backwards buffer as '\n' is for EndOfTransfer
                input.position(input.position() - 1);
                rv = true;
            }
            else if (b == ' ') {
                rv = true;
            }
            else {
                if (buffer.position() == buffer.capacity()) {
                    throw new IllegalArgumentException("payloadLength too long");
                }
                buffer.put(b);
            }
        }
        return rv;
    }
}
