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
package com.teragrep.rlp_03.client;

import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.context.EstablishedContext;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

class ClientDelegate implements FrameDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDelegate.class);

    // TODO perhaps <Integer, Future<Something>> ?
    // TODO what should be Something, RelpFrame is immediately deallocated after FrameDelegate
    // TODO design better: Futures are not optimal for multi-complete/disruptor pattern

    private final ConcurrentHashMap<Integer, CompletableFuture<AbstractMap.SimpleEntry<String, byte[]>>> transactions;

    ClientDelegate() {
        this.transactions = new ConcurrentHashMap<>();
    }

    @Override
    public boolean accept(FrameContext frameContext) {
        LOGGER.debug("client got <[{}]>", frameContext.relpFrame());

        int txn = frameContext.relpFrame().txn().toInt();

        // TODO implement better handling for hint frames
        if (txn == 0) {
            if ("serverclose".equals(frameContext.relpFrame().command().toString())) {
                return false;
            }
            return true;
        }

        CompletableFuture<AbstractMap.SimpleEntry<String, byte[]>> future = transactions.remove(txn);

        if (future == null) {
            throw new IllegalStateException("txn not pending <[" + txn + "]>");
        }

        future
                .complete(
                        new AbstractMap.SimpleEntry<>(
                                frameContext.relpFrame().command().toString(),
                                frameContext.relpFrame().payload().toBytes()
                        )
                );
        LOGGER.debug("completed transaction for <[{}]>", txn);

        frameContext.relpFrame().close();

        return true;
    }

    @Override
    public void close() {
        LOGGER.debug("client FrameDelegate close");
    }

    @Override
    public boolean isStub() {
        return false;
    }

    Client create(EstablishedContext establishedContext) {
        return new Client(establishedContext, transactions);
    }
}
