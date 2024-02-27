package com.teragrep.rlp_03.context.frame;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RelpFrameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpFrameTest.class);

    @Test
    public void testRelpFrameAssembly() {
        RelpFrameImpl relpFrame = new RelpFrameImpl();


        String content = "1 syslog 3 foo\n";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(contentBytes.length);
        input.put(contentBytes);
        input.flip();

        relpFrame.submit(input);

        Assertions.assertEquals(relpFrame.txn().toInt(), 1);
        Assertions.assertEquals(relpFrame.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame.payloadLength().toInt(), 3);
        Assertions.assertEquals(relpFrame.payload().toString(), "foo");
        Assertions.assertArrayEquals(relpFrame.endOfTransfer().toBytes(), new byte[]{'\n'});

        RelpFrameRental relpFrameRental = new RelpFrameRental(relpFrame);
        relpFrameRental.rental().terminate();
        Assertions.assertThrows(IllegalStateException.class, relpFrameRental::toString);
    }
    
    @Test
    public void testRelpFrameAssemblyMultipart() {

    	String content = "7 syslog 6 abcdef\n";
    	byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        RelpFrameImpl relpFrame = new RelpFrameImpl();
    	for (int contentIter = 0; contentIter < contentBytes.length; contentIter++) {
    		// feed one at a time
            // LOGGER.info("contentIter <{}>", contentIter);
    		ByteBuffer input = ByteBuffer.allocateDirect(1);
    		input.put(contentBytes[contentIter]);
    		input.flip();

            if (contentIter < contentBytes.length - 1) {
                Assertions.assertFalse(relpFrame.submit(input));
            }
            else {
                Assertions.assertTrue(relpFrame.submit(input));
            }
    	}

        Assertions.assertEquals(relpFrame.txn().toInt(), 7);
        Assertions.assertEquals(relpFrame.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame.payloadLength().toInt(), 6);
        Assertions.assertEquals(relpFrame.payload().toString(), "abcdef");
        Assertions.assertArrayEquals(relpFrame.endOfTransfer().toBytes(), new byte[]{'\n'});
    }

    @Test
    public void testConsecutiveFrames() {
        String content = "7 syslog 6 abcdef\n8 syslog 6 opqrst\n";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);


        ByteBuffer input = ByteBuffer.allocateDirect(contentBytes.length);
        input.put(contentBytes);
        input.flip();

        // FIRST
        RelpFrameImpl relpFrame1 = new RelpFrameImpl();
        Assertions.assertTrue(relpFrame1.submit(input));

        Assertions.assertEquals(relpFrame1.txn().toInt(), 7);
        Assertions.assertEquals(relpFrame1.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame1.payloadLength().toInt(), 6);
        Assertions.assertEquals(relpFrame1.payload().toString(), "abcdef");
        Assertions.assertArrayEquals(relpFrame1.endOfTransfer().toBytes(), new byte[]{'\n'});

        // SECOND
        RelpFrameImpl relpFrame2 = new RelpFrameImpl();
        Assertions.assertTrue(relpFrame2.submit(input));

        Assertions.assertEquals(relpFrame2.txn().toInt(), 8);
        Assertions.assertEquals(relpFrame2.command().toString(), "syslog");
        Assertions.assertEquals(relpFrame2.payloadLength().toInt(), 6);
        Assertions.assertEquals(relpFrame2.payload().toString(), "opqrst");
        Assertions.assertArrayEquals(relpFrame2.endOfTransfer().toBytes(), new byte[]{'\n'});
    }
}
