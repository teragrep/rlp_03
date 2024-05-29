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

import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class TransactionService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionService.class);
    private static final RelpFrameStub relpFrameStub = new RelpFrameStub();

    private final ConcurrentHashMap<Integer, CompletableFuture<RelpFrame>> transactions;
    private final AtomicBoolean closed;
    private final Lock lock;

    public TransactionService() {
        this.transactions = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean();
        this.lock = new ReentrantLock();
    }

    @Override
    public void close() {
        closed.set(true);
        create(relpFrameStub);
    }

    public CompletableFuture<RelpFrame> create(RelpFrame relpFrame) {
        CompletableFuture<RelpFrame> future = new CompletableFuture<>();

        if (!relpFrame.isStub()) {
            int txn = relpFrame.txn().toInt();
            if (txn != 0) { // hints do not create transactions
                transactions.put(txn, future);
            }
        }
        else {
            if (closed.get()) {
                // drain and completeExceptionally all
                while (!transactions.isEmpty()) {
                    if (lock.tryLock()) {
                        try {
                            transactions
                                    .forEach((integer, relpFrameCompletableFuture) -> relpFrameCompletableFuture.completeExceptionally(new TransactionServiceClosedException("TransactionService closed before transaction was completed.")));
                            transactions.clear();
                        }
                        finally {
                            lock.unlock();
                        }
                    }
                    else {
                        break;
                    }
                }
            }
        }

        return future;
    }

    public void complete(RelpFrame relpFrame) {
        int txn = relpFrame.txn().toInt();

        CompletableFuture<RelpFrame> future = transactions.remove(txn);

        if (future == null) {
            if (!closed.get()) {
                // unless it is closed, this is a serious error
                throw new IllegalStateException("txn not pending <[" + txn + "]>");
            }
        }
        else {
            future.complete(relpFrame);
            LOGGER.debug("completed transaction for <[{}]>", txn);
        }

    }
}
