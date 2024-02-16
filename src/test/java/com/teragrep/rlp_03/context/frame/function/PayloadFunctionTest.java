package com.teragrep.rlp_03.context.frame.function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class PayloadFunctionTest {

    @Test
    public void testParse() {
        PayloadFunction payloadFunction = new PayloadFunction(10);

        String payload = "0123456789more"; // more shouldn't be payload
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(payloadBytes.length);
        input.put(payloadBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        boolean complete = payloadFunction.apply(input, slices);

        Assertions.assertTrue(complete);
        Assertions.assertEquals(1, slices.size());

        Assertions.assertEquals(input.duplicate().limit(input.position()).rewind(), slices.get(0));

        // check more is present
        byte[] moreBytes = new byte[4];
        input.get(moreBytes);
        String more = new String(moreBytes, StandardCharsets.UTF_8);
        Assertions.assertEquals("more", more);
    }
}
