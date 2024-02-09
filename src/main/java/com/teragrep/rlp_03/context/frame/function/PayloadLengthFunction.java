package com.teragrep.rlp_03.context.frame.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class PayloadLengthFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadLengthFunction.class);

    private final int maximumLengthNumbers;
    public PayloadLengthFunction() {
        this.maximumLengthNumbers = String.valueOf(Integer.MAX_VALUE).length();
    }

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {

        ByteBuffer slice = input.slice();
        int bytesRead = 0;
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
            bytesRead++;
            LOGGER.info("input <{}>, b <{}>, bufferSliceList <{}>", input, new String(new byte[]{b}, StandardCharsets.UTF_8), bufferSliceList);
            checkOverSize(bytesRead, bufferSliceList);
            if ( b == '\n') {
                /*
                 '\n' is especially for librelp which should follow:
                 HEADER = TXNR SP COMMAND SP DATALEN SP;
                 but sometimes librelp follows:
                 HEADER = TXNR SP COMMAND SP DATALEN LF; and LF is for EndOfTransfer
                 */
                // seek one byte backwards buffer as '\n' is for EndOfTransfer
                input.position(input.position() - 1);

                slice.limit(bytesRead - 1);

                rv = true;
                break;
            }
            else if (b == ' ') {
                // adjust limit so that bufferSlice contains only this data, without the terminating ' '
                slice.limit(bytesRead - 1);
                rv = true;
                break;
            }
        }

        bufferSliceList.add(slice);

        LOGGER.info("returning <{}> with bufferSliceList <{}>", rv, bufferSliceList);
        return rv;
    }

    private void checkOverSize(int bytesRead, LinkedList<ByteBuffer> bufferSliceList) {
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + slice.limit();
        }

        currentLength = currentLength + bytesRead;
        if (currentLength > maximumLengthNumbers) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("payloadLength too long");
            LOGGER.info("payloadLength oversize <{}>", currentLength, illegalArgumentException);
            throw illegalArgumentException;
        }
    }
}
