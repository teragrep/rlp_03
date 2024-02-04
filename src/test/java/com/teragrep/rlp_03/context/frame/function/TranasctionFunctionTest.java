package com.teragrep.rlp_03.context.frame.function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class TranasctionFunctionTest {
    @Test
    public void testParse() {
        TransactionFunction transactionFunction = new TransactionFunction();

        String transactionId = Integer.MAX_VALUE +  " "; // space is a terminal character
        byte[] transactionIdBytes = transactionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(transactionIdBytes.length);
        input.put(transactionIdBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        boolean complete = transactionFunction.apply(input, slices);

        Assertions.assertTrue(complete);
        Assertions.assertEquals(1, slices.size());

        Assertions.assertEquals(input.duplicate().limit(input.position() - 1).rewind(), slices.get(0));
    }

    @Test
    public void testParseFail() {
        TransactionFunction transactionFunction = new TransactionFunction();

        String tranasctionId = Integer.MAX_VALUE +  "1 "; // add one more, space is a terminal character
        byte[] tranasctionIdBytes = tranasctionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(tranasctionIdBytes.length);
        input.put(tranasctionIdBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            boolean complete = transactionFunction.apply(input, slices);
        }, "tranasctionId too long");
    }
}
