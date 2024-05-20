/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
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
package com.teragrep.rlp_03.channel.context.frame.clocks;

import com.teragrep.rlp_03.frame.fragment.Fragment;
import com.teragrep.rlp_03.frame.fragment.clocks.TransactionClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TransactionClockTest {

    @Test
    public void testStub() {
        TransactionClock transactionClock = new TransactionClock();
        Fragment txn = transactionClock.submit(ByteBuffer.allocateDirect(0));
        Assertions.assertTrue(txn.isStub());
    }

    @Test
    public void testParse() {
        TransactionClock transactionClock = new TransactionClock();

        String transactionId = "999999999 "; // space is a terminal character
        byte[] transactionIdBytes = transactionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(transactionIdBytes.length);
        input.put(transactionIdBytes);
        input.flip();

        Fragment txn = transactionClock.submit(input);

        Assertions.assertFalse(txn.isStub());

        Assertions.assertEquals(999999999, txn.toInt());

        // consecutive
        input.rewind();
        Fragment otherTxn = transactionClock.submit(input);
        Assertions.assertFalse(otherTxn.isStub());
        Assertions.assertEquals(999999999, otherTxn.toInt());
    }

    @Test
    public void testParseFail() {
        TransactionClock transactionFunction = new TransactionClock();

        String tranasctionId = "9999999991 "; // add one more, space is a terminal character
        byte[] tranasctionIdBytes = tranasctionId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocateDirect(tranasctionIdBytes.length);
        input.put(tranasctionIdBytes);
        input.flip();

        Assertions
                .assertThrows(IllegalArgumentException.class, () -> transactionFunction.submit(input), "tranasctionId too long");
    }
}
