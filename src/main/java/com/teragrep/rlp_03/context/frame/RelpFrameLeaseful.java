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

import com.teragrep.rlp_03.context.buffer.BufferLease;
import com.teragrep.rlp_03.context.buffer.BufferLeasePool;
import com.teragrep.rlp_03.context.frame.fragment.Fragment;

import java.util.LinkedList;
import java.util.List;

public class RelpFrameLeaseful implements RelpFrame, AutoCloseable {

    private final BufferLeasePool bufferLeasePool;
    private final RelpFrameImpl relpFrame;

    private final List<BufferLease> leases;

    public RelpFrameLeaseful(BufferLeasePool bufferLeasePool, RelpFrameImpl relpFrame) {
        this.bufferLeasePool = bufferLeasePool;
        this.relpFrame = relpFrame;
        this.leases = new LinkedList<>();
    }

    @Override
    public Fragment txn() {
        return relpFrame.txn();
    }

    @Override
    public Fragment command() {
        return relpFrame.command();
    }

    @Override
    public Fragment payloadLength() {
        return relpFrame.payloadLength();
    }

    @Override
    public Fragment payload() {
        return relpFrame.payload();
    }

    @Override
    public Fragment endOfTransfer() {
        return relpFrame.endOfTransfer();
    }

    @Override
    public boolean isStub() {
        return relpFrame.isStub();
    }

    public boolean submit(BufferLease bufferLease) {
        leases.add(bufferLease);
        return relpFrame.submit(bufferLease.buffer());
    }

    @Override
    public void close() {
        // return buffers
        for (BufferLease bufferLease : leases) {
            /*
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("releasing id <{}> with refs <{}>", bufferLease.id(), bufferLease.refs());
            }
             */
            bufferLeasePool.offer(bufferLease);
        }
    }

    @Override
    public String toString() {
        return "RelpFrameLeaseful{" +
                "relpFrame=" + relpFrame +
                ", leaseSet=" + "REMOVED" +
                '}';
    }
}
