package com.teragrep.rlp_03.context.frame.function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class EndOfTransferFunctionTest {

    @Test
    public void testParse() {
        EndOfTransferFunction endOfTransferFunction = new EndOfTransferFunction();

        String endOfTransfer = "\nX"; // characters allowed after eot string
        byte[] endOfTransferBytes = endOfTransfer.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(endOfTransferBytes.length);
        input.put(endOfTransferBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        boolean complete = endOfTransferFunction.apply(input, slices);

        Assertions.assertTrue(complete);
        Assertions.assertEquals(1, slices.size());

        // trailing space is removed from slices as it is not part of the command but a terminal character
        Assertions.assertEquals(input.duplicate().limit(input.position()).rewind(), slices.get(0));
    }

    @Test
    public void testParseFail() {
        EndOfTransferFunction endOfTransferFunction = new EndOfTransferFunction();

        String endOfTransfer = "x"; // characters allowed after eot string
        byte[] endOfTransferBytes = endOfTransfer.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(endOfTransferBytes.length);
        input.put(endOfTransferBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            boolean complete = endOfTransferFunction.apply(input, slices);
        }, "no match for EndOfTransfer character \\n");

    }

}
