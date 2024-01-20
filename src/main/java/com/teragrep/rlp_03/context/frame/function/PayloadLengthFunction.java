package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class PayloadLengthFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    private final int maximumFrameSize = 256*1024;

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
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

                ByteBuffer bufferSlice = (ByteBuffer) input.duplicate().limit(input.position());
                bufferSlice.rewind();
                bufferSliceList.add(bufferSlice);

                rv = true;
                break;
            }
            else if (b == ' ') {
                // adjust limit so that bufferSlice contains only this data, without the terminating ' '
                ByteBuffer bufferSlice = (ByteBuffer) input.duplicate().limit(input.position() - 1);
                bufferSlice.rewind();
                bufferSliceList.add(bufferSlice);

                rv = true;
                break;
            }
        }

        if (!rv) {
            // whole input is part of this
            input.rewind();
            bufferSliceList.add(input);
        }

        return rv;
    }
}
