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
package com.teragrep.rlp_03.channel.context.frame.fragment;

import com.teragrep.rlp_03.frame.fragment.Fragment;
import com.teragrep.rlp_03.frame.fragment.FragmentByteStream;
import com.teragrep.rlp_03.frame.fragment.FragmentImpl;
import com.teragrep.rlp_03.frame.function.TransactionFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FragmentTest {

    @Test
    public void testFragment() {
        TransactionFunction transactionFunction = new TransactionFunction();
        Fragment fragment = new FragmentImpl(transactionFunction);

        Assertions.assertFalse(fragment.isComplete());

        // these must throw because it's not complete
        Assertions.assertThrows(IllegalStateException.class, fragment::size);
        Assertions.assertThrows(IllegalStateException.class, fragment::toBytes);
        Assertions.assertThrows(IllegalStateException.class, fragment::toString);
        Assertions.assertThrows(IllegalStateException.class, fragment::toInt);
        Assertions.assertThrows(IllegalStateException.class, fragment::toFragmentWrite);
        Assertions.assertThrows(IllegalStateException.class, fragment::toFragmentByteStream);

        String txn = "123 ";
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();

        fragment.accept(byteBuffer);
        Assertions.assertTrue(fragment.isComplete());
        Assertions.assertEquals(3, fragment.size());

        // conversions
        Assertions.assertArrayEquals(new byte[] {
                49, 50, 51
        }, fragment.toBytes());
        Assertions.assertEquals("123", fragment.toString());
        Assertions.assertEquals(123, fragment.toInt());
        // TODO fragment.toFragmentWrite().write()
        FragmentByteStream fragmentByteStream = fragment.toFragmentByteStream();

        // FragmentByteStream
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 49), fragmentByteStream.get());
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 50), fragmentByteStream.get());
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 51), fragmentByteStream.get());
        Assertions.assertFalse(fragmentByteStream.next());

    }
}
