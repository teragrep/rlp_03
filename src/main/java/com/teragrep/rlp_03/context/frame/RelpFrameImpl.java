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

import com.teragrep.rlp_03.context.frame.fragment.Fragment;
import com.teragrep.rlp_03.context.frame.fragment.FragmentImpl;
import com.teragrep.rlp_03.context.frame.fragment.FragmentStub;
import com.teragrep.rlp_03.context.frame.function.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;


// TODO Design how to use Access properly if RelpFrames are also poolable
public class RelpFrameImpl implements RelpFrame {
    private final Fragment txn;
    private final Fragment command;
    private final Fragment payloadLength;
    private Fragment payload;
    private final Fragment endOfTransfer;

    public RelpFrameImpl() {
        this.txn = new FragmentImpl(new TransactionFunction());
        this.command = new FragmentImpl(new CommandFunction());
        this.payloadLength = new FragmentImpl(new PayloadLengthFunction());
        this.payload = new FragmentStub();
        this.endOfTransfer = new FragmentImpl(new EndOfTransferFunction());
    }

    public boolean submit(ByteBuffer input) {
        boolean rv = false;

        while (input.hasRemaining() && !rv) {

            if (!txn.isComplete()) {
                txn.accept(input);
            } else if (!command.isComplete()) {
                command.accept(input);
            } else if (!payloadLength.isComplete()) {
                payloadLength.accept(input);

                if (payloadLength.isComplete()) {
                    // PayloadFunction depends on payload length and needs to by dynamically created
                    int payloadSize = payloadLength.toInt();
                    payload = new FragmentImpl(new PayloadFunction(payloadSize));
                }
            } else if (!payload.isComplete()) {
                payload.accept(input);
            } else if (!endOfTransfer.isComplete()) {
                endOfTransfer.accept(input);

                if (endOfTransfer.isComplete()) {
                    // all complete
                    rv = true;
                }
            } else {
                throw new IllegalStateException("submit not allowed on a complete frame");
            }
        }
        return rv;
    }

    @Override
    public Fragment txn() {
        return txn;
    }

    @Override
    public Fragment command() {
        return command;
    }

    @Override
    public Fragment payloadLength() {
        return payloadLength;
    }

    @Override
    public Fragment payload() {
        return payload;
    }

    @Override
    public Fragment endOfTransfer() {
        return endOfTransfer;
    }

    @Override
    public boolean isStub() {
        return false;
    }

    @Override
    public String toString() {
        return "RelpFrameImpl{" +
                "txn=" + txn +
                ", command=" + command +
                ", payloadLength=" + payloadLength +
                ", payload=" + payload +
                ", endOfTransfer=" + endOfTransfer +
                '}';
    }
}
