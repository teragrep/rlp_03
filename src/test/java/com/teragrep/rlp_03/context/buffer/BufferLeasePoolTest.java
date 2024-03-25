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
package com.teragrep.rlp_03.context.buffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BufferLeasePoolTest {

    @Test
    public void testPool() {
        BufferLeasePool bufferLeasePool = new BufferLeasePool();
        List<BufferLease> leases = bufferLeasePool.take(1);

        Assertions.assertEquals(1, leases.size());

        Assertions.assertEquals(0, bufferLeasePool.estimatedSize()); // none in the pool

        BufferLease lease = leases.get(0);

        Assertions.assertFalse(lease.isStub());

        Assertions.assertFalse(lease.isRefCountZero()); // initially 1 refs

        Assertions.assertEquals(1, lease.refs()); // check initial 1 ref

        lease.addRef();

        Assertions.assertEquals(2, lease.refs());

        lease.buffer().put((byte) 'x');

        Assertions.assertEquals(1, lease.buffer().position());

        lease.buffer().flip();

        Assertions.assertEquals(0, lease.buffer().position());

        Assertions.assertEquals(1, lease.buffer().limit());

        Assertions.assertEquals((byte) 'x', lease.buffer().get());

        Assertions.assertEquals(1, lease.buffer().position());

        Assertions.assertEquals(2, lease.refs());

        lease.removeRef();

        Assertions.assertFalse(lease.isRefCountZero()); // initial ref must be still in place

        Assertions.assertEquals(1, lease.refs()); // initial ref must be still in

        bufferLeasePool.offer(lease); // removes initial ref

        Assertions.assertEquals(1, bufferLeasePool.estimatedSize()); // the one offered must be there

        Assertions.assertTrue(lease.isRefCountZero()); // no refs

        Assertions.assertEquals(lease.buffer().capacity(), lease.buffer().limit());

        Assertions.assertEquals(0, lease.buffer().position());

        bufferLeasePool.close();

        Assertions.assertEquals(0, bufferLeasePool.estimatedSize());
    }
}
