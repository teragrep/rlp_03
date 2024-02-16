package com.teragrep.rlp_03.context.frame.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.function.BiFunction;

public class TransactionFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    //private static final Logger LOGGER = LoggerFactory.getLogger(TransactionFunction.class);

    private static final int maximumIdNumbers  = String.valueOf(Integer.MAX_VALUE).length() + 1; // space
    public TransactionFunction() {
    }


    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {

        ByteBuffer slice = input.slice();
        int bytesRead = 0;
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
            bytesRead++;
            checkOverSize(bytesRead, bufferSliceList);
            //LOGGER.info("read byte b <{}>", new String(new byte[]{b}, StandardCharsets.UTF_8));
            if (b == ' ') {
                ((ByteBuffer) slice).limit(bytesRead - 1);
                rv = true;
                break;
            }
        }

        bufferSliceList.add(slice);

        return rv;
    }

    private void checkOverSize(int bytesRead, LinkedList<ByteBuffer> bufferSliceList) {
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + ((ByteBuffer) slice).limit();
        }

        currentLength = currentLength + bytesRead;
        if (currentLength > maximumIdNumbers) {
            throw new IllegalArgumentException("payloadLength too long");
        }
    }
}
