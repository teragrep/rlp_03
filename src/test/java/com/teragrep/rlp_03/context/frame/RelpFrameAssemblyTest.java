package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.function.TransactionFunction;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RelpFrameAssemblyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpFrameAssemblyTest.class);

    @Test
    public void testRelpFrameAssembly() {
        RelpFrameAssembly relpFrameAssembly = new RelpFrameAssembly();


        String content = "1 syslog 3 foo\n";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(contentBytes.length);
        input.put(contentBytes);
        input.flip();

        RelpFrame relpFrame = relpFrameAssembly.submit(input);

        LOGGER.info("relpFrame <{}>", relpFrame);

        // TODO test throws after free
        relpFrameAssembly.free(relpFrame);

        LOGGER.info("relpFrame <{}>", relpFrame);
    }
}
