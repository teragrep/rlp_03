/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021  Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */

package com.teragrep.rlp_03.context.frame;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RelpFrameTest {
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

        RelpFrameAccess relpFrameAccess = new RelpFrameAccess(relpFrame);
        relpFrameAccess.access().terminate();
        Assertions.assertThrows(IllegalStateException.class, relpFrameAccess::toString);
    }
    
    @Test
    public void testRelpFrameAssemblyMultipart() {

    	String content = "7 syslog 6 abcdef\n";
    	byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        RelpFrameImpl relpFrame = new RelpFrameImpl();
    	for (int contentIter = 0; contentIter < contentBytes.length; contentIter++) {
    		// feed one at a time
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
