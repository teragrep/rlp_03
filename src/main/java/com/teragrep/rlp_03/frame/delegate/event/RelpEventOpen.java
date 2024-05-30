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

import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameImpl;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.fragment.Fragment;
import com.teragrep.rlp_03.frame.fragment.FragmentFactory;
import com.teragrep.rlp_03.frame.fragment.FragmentStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelpEventOpen extends RelpEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpEventOpen.class);
    private final FragmentFactory fragmentFactory;
    private final RelpFrame responseFrameTemplate;

    public RelpEventOpen() {
        this.fragmentFactory = new FragmentFactory();
        // TODO create FragmentFactory
        Fragment txn = new FragmentStub();
        Fragment command = fragmentFactory.create("rsp");
        String content = "200 OK\nrelp_version=0\nrelp_software=RLP-01,1.0.1,https://teragrep.com\ncommands=syslog\n";
        Fragment payload = fragmentFactory.create(content);
        long payloadSize = payload.size();
        Fragment payloadLength = fragmentFactory.create(payloadSize);
        Fragment endOfTransfer = fragmentFactory.create("\n");
        this.responseFrameTemplate = new RelpFrameImpl(txn, command, payloadLength, payload, endOfTransfer);
    }

    @Override
    public void accept(FrameContext frameContext) {
        try {
            Fragment txnCopy = fragmentFactory.wrap(frameContext.relpFrame().txn().toBytes()); // TODO remove once #185
            RelpFrame frame = new RelpFrameImpl(
                    txnCopy,
                    responseFrameTemplate.command(),
                    responseFrameTemplate.payloadLength(),
                    responseFrameTemplate.payload(),
                    responseFrameTemplate.endOfTransfer()
            );

            frameContext.establishedContext().relpWrite().accept(frame.toWriteable());
        }
        finally {
            frameContext.relpFrame().close();
        }
    }

}
