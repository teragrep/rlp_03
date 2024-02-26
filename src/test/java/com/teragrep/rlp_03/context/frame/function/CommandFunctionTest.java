package com.teragrep.rlp_03.context.frame.function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class CommandFunctionTest {

    @Test
    public void testParse() {
        CommandFunction commandFunction = new CommandFunction();

        String command = "syslog "; // traling space terminates command
        byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(commandBytes.length);
        input.put(commandBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        boolean complete = commandFunction.apply(input, slices);

        Assertions.assertTrue(complete);
        Assertions.assertEquals(1, slices.size());

        // trailing space is removed from slices as it is not part of the command but a terminal character
        Assertions.assertEquals(input.duplicate().limit(input.position() - 1).rewind(), slices.get(0));
    }

    @Test
    public void testParseFail() {
        CommandFunction commandFunction = new CommandFunction();

        String command = "servercloseX "; // traling space terminates command
        byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(commandBytes.length);
        input.put(commandBytes);
        input.flip();

        LinkedList<ByteBuffer> slices = new LinkedList<>();
        Assertions.assertThrows(IllegalArgumentException.class, () -> commandFunction.apply(input, slices), "command too long");
    }
}
