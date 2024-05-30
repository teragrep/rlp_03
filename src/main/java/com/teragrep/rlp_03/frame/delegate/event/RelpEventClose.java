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
package com.teragrep.rlp_03.frame.delegate.event;

import com.teragrep.rlp_03.channel.context.Writeable;
import com.teragrep.rlp_03.channel.context.WriteableClosure;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameImpl;
import com.teragrep.rlp_03.channel.context.Writeables;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.fragment.Fragment;
import com.teragrep.rlp_03.frame.fragment.FragmentFactory;
import com.teragrep.rlp_03.frame.fragment.FragmentStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class RelpEventClose extends RelpEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpEventClose.class);

    private final RelpFrame closeFrameTemplate;
    private final RelpFrame serverCloseFrame;
    private final FragmentFactory fragmentFactory;

    public RelpEventClose() {
        this.fragmentFactory = new FragmentFactory();

        Fragment closeResponseTxn = new FragmentStub();

        Fragment closeResponseCommand = fragmentFactory.create("rsp");
        Fragment closeResponsePayload = fragmentFactory.create("");
        Fragment closeResponsePayloadLength = fragmentFactory.create(closeResponsePayload.size());
        Fragment closeResponseEndOfTransfer = fragmentFactory.create("\n");

        this.closeFrameTemplate = new RelpFrameImpl(
                closeResponseTxn,
                closeResponseCommand,
                closeResponsePayloadLength,
                closeResponsePayload,
                closeResponseEndOfTransfer
        );

        Fragment serverCloseTxn = fragmentFactory.create(0);
        Fragment serverCloseCommand = fragmentFactory.create("serverclose");
        Fragment serverClosePayload = fragmentFactory.create("");
        Fragment serverClosePayloadLength = fragmentFactory.create(serverClosePayload.size());
        Fragment serverCloseEndOfTransfer = fragmentFactory.create("\n");

        this.serverCloseFrame = new RelpFrameImpl(
                serverCloseTxn,
                serverCloseCommand,
                serverClosePayloadLength,
                serverClosePayload,
                serverCloseEndOfTransfer
        );
    }

    @Override
    public void accept(FrameContext frameContext) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("received close on txn <[{}]>", frameContext.relpFrame().txn().toString());
            }

            Fragment txn = frameContext.relpFrame().txn();
            RelpFrame relpFrame = new RelpFrameImpl(
                    txn,
                    closeFrameTemplate.command(),
                    closeFrameTemplate.payloadLength(),
                    closeFrameTemplate.payload(),
                    closeFrameTemplate.endOfTransfer()
            );

            List<Writeable> framesWriteables = new ArrayList<>();
            framesWriteables.add(relpFrame.toWriteable());
            framesWriteables.add(serverCloseFrame.toWriteable());

            Writeables writeables = new Writeables(framesWriteables);
            WriteableClosure writeableClosure = new WriteableClosure(writeables, frameContext.establishedContext());

            frameContext.establishedContext().relpWrite().accept(writeableClosure);
        }
        finally {
            frameContext.relpFrame().close();
        }

    }

}
