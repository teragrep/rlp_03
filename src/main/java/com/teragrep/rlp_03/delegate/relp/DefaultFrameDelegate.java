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

package com.teragrep.rlp_03.delegate.relp;


import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


public class DefaultFrameDelegate implements FrameDelegate {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFrameDelegate.class);

    private final Map<String, RelpEvent> relpCommandConsumerMap;
    private final RelpEvent relpEventServerClose;
    private final AtomicInteger txId;

    public DefaultFrameDelegate(Consumer<FrameContext> cbFunction) {
        this(new HashMap<>());
        relpCommandConsumerMap.put(RelpCommand.CLOSE, new RelpEventClose());
        relpCommandConsumerMap.put(RelpCommand.OPEN, new RelpEventOpen());
        relpCommandConsumerMap.put(RelpCommand.SYSLOG, new RelpEventSyslog(cbFunction));
    }

    public DefaultFrameDelegate(Map<String, RelpEvent> relpCommandConsumerMap) {
        this.relpCommandConsumerMap = relpCommandConsumerMap;
        this.relpEventServerClose = new RelpEventServerClose();
        this.txId = new AtomicInteger();
    }

    @Override
    public boolean accept(FrameContext frameContext) {
        boolean rv = true;

        int nextTxnId = txId.incrementAndGet();

        if (nextTxnId == 999_999_999) {
            // wraps around after 999999999
            txId.set(1);
        }

        if (txId.incrementAndGet() != frameContext.relpFrame().txn().toInt()) {
            throw new IllegalArgumentException("frame txn not sequencing");
        }

        String relpCommand = frameContext.relpFrame().command().toString();

        Consumer<FrameContext> commandConsumer = relpCommandConsumerMap.getOrDefault(relpCommand, relpEventServerClose);

        commandConsumer.accept(frameContext);


        if (RelpCommand.CLOSE.equals(relpCommand)) {
            // TODO refactor commandConsumer to return indication of further reads
            rv = false;
        }

        return rv;
    }

    @Override
    public void close() throws Exception {
        for (AutoCloseable autoCloseable : relpCommandConsumerMap.values()) {
            autoCloseable.close();
        }
    }

    @Override
    public boolean isStub() {
        return false;
    }

}

