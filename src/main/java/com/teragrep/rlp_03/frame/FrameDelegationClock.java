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
package com.teragrep.rlp_03.frame;

import com.teragrep.rlp_03.channel.buffer.BufferLease;

import com.teragrep.rlp_03.channel.context.Clock;
import com.teragrep.rlp_03.channel.context.EstablishedContext;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrameDelegationClock implements Clock {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameDelegationClock.class);

    private final EstablishedContext establishedContext;
    private final FrameDelegate frameDelegate;

    private final FrameClockLeaseful frameClockLeaseful;

    public FrameDelegationClock(EstablishedContext establishedContext, FrameDelegate frameDelegate) {
        this.establishedContext = establishedContext;
        this.frameDelegate = frameDelegate;

        this.frameClockLeaseful = new FrameClockLeaseful(new FrameClock());
    }

    @Override
    public boolean advance(BufferLease bufferLease) {
        LOGGER.debug("submitting bufferLease id <{}>", bufferLease.id());
        RelpFrame relpFrame = frameClockLeaseful.submit(bufferLease);

        if (bufferLease.buffer().hasRemaining()) { // TODO should this even be responsibility of a Clock, probably not?
            bufferLease.addRef(); // a shared buffer
        }
        LOGGER.debug("bufferLease id <{}> hasRemaining <{}>", bufferLease.id(), bufferLease.buffer().remaining());

        boolean rv;
        if (relpFrame.isStub()) {
            rv = true;
        }
        else {
            LOGGER
                    .debug(
                            "bufferLease id <{}> before delegate hasRemaining <{}>", bufferLease.id(),
                            bufferLease.buffer().hasRemaining()
                    );
            rv = delegateFrame(relpFrame);
            LOGGER
                    .debug(
                            "bufferLease id <{}> after delegate isTerminated <{}>", bufferLease.id(),
                            bufferLease.isTerminated()
                    );
        }

        LOGGER.debug("bufferLease id <{}> isTerminated <{}>", bufferLease.id(), bufferLease.isTerminated());

        return rv;
    }

    private boolean delegateFrame(RelpFrame relpFrame) {
        boolean rv;

        RelpFrameAccess relpFrameAccess = new RelpFrameAccess(relpFrame);
        FrameContext frameContext = new FrameContext(establishedContext, relpFrameAccess);

        rv = frameDelegate.accept(frameContext);

        LOGGER.debug("processed txFrame.");
        return rv;
    }

    @Override
    public void close() throws Exception {
        frameDelegate.close();
    }

}
