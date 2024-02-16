package com.teragrep.rlp_03.context.frame.function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class PayloadLengthFunctionTest {

    @Test
    public void testParse() {
        PayloadLengthFunction payloadLengthFunction = new PayloadLengthFunction();

        String payloadLength = Integer.MAX_VALUE +  " "; // space is a terminal character
        byte[] payloadLengthBytes = payloadLength.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(payloadLengthBytes.length);
        input.put(payloadLengthBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        boolean complete = payloadLengthFunction.apply(input, slices);

        Assertions.assertTrue(complete);
        Assertions.assertEquals(1, slices.size());

        Assertions.assertEquals(input.duplicate().limit(input.position() - 1).rewind(), slices.get(0));
    }

    @Test
    public void testParseFail() {
        PayloadLengthFunction payloadLengthFunction = new PayloadLengthFunction();

        String payloadLength = Integer.MAX_VALUE +  "1 "; // add one more, space is a terminal character
        byte[] payloadLengthBytes = payloadLength.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(payloadLengthBytes.length);
        input.put(payloadLengthBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            boolean complete = payloadLengthFunction.apply(input, slices);
        }, "payloadLength too long");
    }
}
