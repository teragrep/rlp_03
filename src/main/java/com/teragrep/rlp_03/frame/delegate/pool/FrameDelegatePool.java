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
package com.teragrep.rlp_03.frame.delegate.pool;

import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameDelegateStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FrameDelegatePool implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameDelegatePool.class);

    private final Supplier<FrameDelegate> frameDelegateSupplier;

    private final ConcurrentLinkedQueue<FrameDelegate> queue;

    private final FrameDelegate frameDelegateStub;

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean close;

    public FrameDelegatePool(final Supplier<FrameDelegate> frameDelegateSupplier) {
        this.frameDelegateSupplier = frameDelegateSupplier;
        this.queue = new ConcurrentLinkedQueue<>();
        this.frameDelegateStub = new FrameDelegateStub();
        this.close = new AtomicBoolean();

        // TODO maximum number of available frameDelegates should be perhaps limited?
    }

    public FrameDelegate take() {
        FrameDelegate frameDelegate;
        if (close.get()) {
            frameDelegate = frameDelegateStub;
        }
        else {
            // get or create
            frameDelegate = queue.poll();
            if (frameDelegate == null) {
                frameDelegate = frameDelegateSupplier.get();
            }
        }

        return frameDelegate;
    }

    public void offer(FrameDelegate frameDelegate) {
        if (!frameDelegate.isStub()) {
            queue.add(frameDelegate);
        }

        if (close.get()) {
            while (queue.peek() != null) {
                if (lock.tryLock()) {
                    while (true) {
                        FrameDelegate queuedFrameDelegate = queue.poll();
                        if (queuedFrameDelegate == null) {
                            break;
                        }
                        else {
                            try {
                                LOGGER.debug("Closing frameDelegate <{}>", queuedFrameDelegate);
                                queuedFrameDelegate.close();
                                LOGGER.debug("Closed frameDelegate <{}>", queuedFrameDelegate);
                            }
                            catch (Exception exception) {
                                LOGGER
                                        .warn(
                                                "Exception <{}> while closing frameDelegate <{}>",
                                                exception.getMessage(), queuedFrameDelegate
                                        );
                            }
                        }
                    }
                    lock.unlock();
                }
                else {
                    break;
                }
            }
        }
    }

    public void close() {
        close.set(true);

        // close all that are in the pool right now
        offer(frameDelegateStub);
    }
}
