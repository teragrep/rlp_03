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
package com.teragrep.rlp_03.channel.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

final class RelpWriteImpl implements RelpWrite {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpWriteImpl.class);

    private final EstablishedContext establishedContext;

    private final ConcurrentLinkedQueue<Writeable> queue;

    private final Lock lock;

    // tls
    private final AtomicBoolean needRead;

    RelpWriteImpl(EstablishedContext establishedContext) {
        this.establishedContext = establishedContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
        this.needRead = new AtomicBoolean();
    }

    // this must be thread-safe!
    @Override
    public void accept(Writeable writeable) {

        if (!writeable.isStub()) {
            queue.add(writeable);
        }

        while (queue.peek() != null) {
            if (lock.tryLock()) {
                try {
                    while (true) {
                        // peek first, it may be partially written
                        Writeable aboutToWrite = queue.peek();
                        if (aboutToWrite == null) {
                            break;
                        }

                        try {
                            if (transmit(aboutToWrite)) {
                                // remove completely written writeable
                                Writeable written = queue.poll();
                                if (written == null) {
                                    throw new IllegalStateException(
                                            "send queue was empty, while it should have contained last sent frame"
                                    );
                                }

                                LOGGER.debug("complete write, closing written writeable");
                                written.close();
                            }
                        }
                        catch (CancelledKeyException cke) {
                            LOGGER
                                    .warn(
                                            "CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>",
                                            cke.getMessage(),
                                            establishedContext.socket().getTransportInfo().getPeerAddress(),
                                            establishedContext.socket().getTransportInfo().getPeerPort()
                                    );
                            establishedContext.close();
                            return;
                        }
                        catch (IOException ioException) {
                            LOGGER
                                    .error(
                                            "IOException <{}> while writing to socket. PeerAddress <{}> PeerPort <{}>",
                                            ioException,
                                            establishedContext.socket().getTransportInfo().getPeerAddress(),
                                            establishedContext.socket().getTransportInfo().getPeerPort()
                                    );
                            establishedContext.close();
                            return;
                        }
                    }
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

    private boolean transmit(Writeable writeable) throws IOException {
        boolean writeComplete = false;

        try {
            List<ByteBuffer> bufferList = writeable.buffers();
            ByteBuffer[] buffers = bufferList.toArray(new ByteBuffer[0]);
            establishedContext.socket().write(buffers);

            boolean allDone = true;
            for (ByteBuffer buffer : buffers) {
                if (buffer.hasRemaining()) {
                    allDone = false;
                    break;
                }
            }
            if (allDone) {
                writeComplete = true;
            }
        }
        catch (NeedsReadException nre) {
            needRead.set(true);
            establishedContext.interestOps().add(OP_READ);
        }
        catch (NeedsWriteException nwe) {
            establishedContext.interestOps().add(OP_WRITE);
        }

        return writeComplete;
    }

    @Override
    public void run() {
        accept(new WriteableStub());
    }

    @Override
    public AtomicBoolean needRead() {
        return needRead;
    }
}
