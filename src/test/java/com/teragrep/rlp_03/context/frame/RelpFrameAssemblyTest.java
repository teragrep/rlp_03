package com.teragrep.rlp_03.context.frame;

import org.junit.jupiter.api.Assertions;
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

        Assertions.assertEquals(relpFrame.txn().toInt(), 1);
        Assertions.assertEquals(relpFrame.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame.payloadLength().toInt(), 3);
        Assertions.assertEquals(relpFrame.payload().toString(), "foo");
        Assertions.assertArrayEquals(relpFrame.endOfTransfer().toBytes(), new byte[]{'\n'});
        
        relpFrameAssembly.free(relpFrame);

        Assertions.assertThrows(IllegalStateException.class, relpFrame::toString);
    }
    
    @Test
    public void testRelpFrameAssemblyMultipart() {
    	RelpFrameAssembly relpFrameAssembly = new RelpFrameAssembly();
    	
    	String content = "7 syslog 6 abcdef\n";
    	byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        RelpFrame relpFrame = new RelpFrameStub();
    	for (int contentIter = 0; contentIter < contentBytes.length; contentIter++) {
    		// feed one at a time
            LOGGER.info("contentIter <{}>", contentIter);
    		ByteBuffer input = ByteBuffer.allocateDirect(1);
    		input.put(contentBytes[contentIter]);
    		input.flip();

            Assertions.assertTrue(relpFrame.isStub());
            relpFrame = relpFrameAssembly.submit(input);
    	}
        Assertions.assertFalse(relpFrame.isStub());

        Assertions.assertEquals(relpFrame.txn().toInt(), 7);
        Assertions.assertEquals(relpFrame.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame.payloadLength().toInt(), 6);
        Assertions.assertEquals(relpFrame.payload().toString(), "abcdef");
        Assertions.assertArrayEquals(relpFrame.endOfTransfer().toBytes(), new byte[]{'\n'});    }
}
