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

import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.fragment.FragmentWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;

import java.nio.channels.CancelledKeyException;
import java.util.ArrayList;
import java.util.Collections;
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

    private final ConcurrentLinkedQueue<RelpFrame> queue;

    private final Lock lock;

    private final ArrayList<FragmentWrite> currentFragmentWrites;
    // tls
    private final AtomicBoolean needRead;

    RelpWriteImpl(EstablishedContext establishedContext) {
        this.establishedContext = establishedContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
        this.currentFragmentWrites = new ArrayList<>(5);
        this.needRead = new AtomicBoolean();
    }

    // this must be thread-safe!
    @Override
    public void accept(List<RelpFrame> relpFrames) {
        LOGGER.trace("Accepting <[{}]>", relpFrames);

        if (!relpFrames.isEmpty()) {
            queue.addAll(relpFrames);
        }

        while (queue.peek() != null) {
            if (lock.tryLock()) {
                try {
                    while (true) {
                        // peek first, it may be partially wrtten
                        RelpFrame frameTX = queue.peek();
                        if (frameTX == null) {
                            break;
                        }

                        currentFragmentWrites.add(frameTX.txn().toFragmentWrite());
                        currentFragmentWrites.add(frameTX.command().toFragmentWrite());
                        currentFragmentWrites.add(frameTX.payloadLength().toFragmentWrite());
                        currentFragmentWrites.add(frameTX.payload().toFragmentWrite());
                        currentFragmentWrites.add(frameTX.endOfTransfer().toFragmentWrite());

                        if (sendFragments(currentFragmentWrites)) {
                            // remove completely written frame
                            RelpFrame sentFrame = queue.poll();
                            if (sentFrame == null) {
                                throw new IllegalStateException("send queue was empty, while it should have contained last sent frame");
                            }

                            LOGGER.debug("complete write");
                            if ("serverclose".equals(sentFrame.command().toString())) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER
                                            .debug(
                                                    "Sent command <{}>, Closing connection to  PeerAddress <{}> PeerPort <{}>",
                                                    "serverclose",
                                                    establishedContext.socket().getTransportInfo().getPeerAddress(),
                                                    establishedContext.socket().getTransportInfo().getPeerPort()
                                            );
                                }
                                establishedContext.close();
                            }
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

    private boolean sendFragments(ArrayList<FragmentWrite> fragmentsToSend) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("fragmentsToSend <{}>", fragmentsToSend);
        }

        try {
            for (FragmentWrite fragmentWrite : fragmentsToSend) {
                long bytesWritten = fragmentWrite.write(establishedContext.socket().socketChannel());

                if (bytesWritten < 0) {
                    LOGGER
                            .error(
                                    "Socket write returns <{}>. Closing connection to  PeerAddress <{}> PeerPort <{}>",
                                    bytesWritten, establishedContext.socket().getTransportInfo().getPeerAddress(),
                                    establishedContext.socket().getTransportInfo().getPeerPort()
                            );
                    // close connection
                    establishedContext.close();
                    return false;
                }

                if (fragmentWrite.hasRemaining()) {
                    // partial write
                    break;
                }
                else {
                    fragmentsToSend.remove(fragmentWrite);
                }
            }

        }
        catch (NeedsReadException nre) {
            needRead.set(true);
            try {
                establishedContext.interestOps().add(OP_READ);
            }
            catch (CancelledKeyException cke) {
                LOGGER
                        .warn(
                                "CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>",
                                cke.getMessage(), establishedContext.socket().getTransportInfo().getPeerAddress(),
                                establishedContext.socket().getTransportInfo().getPeerPort()
                        );
                establishedContext.close();
            }
            return false;
        }
        catch (NeedsWriteException nwe) {
            try {
                establishedContext.interestOps().add(OP_WRITE);
            }
            catch (CancelledKeyException cke) {
                LOGGER
                        .warn(
                                "CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>",
                                cke.getMessage(), establishedContext.socket().getTransportInfo().getPeerAddress(),
                                establishedContext.socket().getTransportInfo().getPeerPort()
                        );
                establishedContext.close();
            }
            return false;
        }
        catch (IOException ioException) {
            LOGGER
                    .error(
                            "IOException <{}> while writing to socket. PeerAddress <{}> PeerPort <{}>", ioException,
                            establishedContext.socket().getTransportInfo().getPeerAddress(),
                            establishedContext.socket().getTransportInfo().getPeerPort()
                    );
            establishedContext.close();
            return false;
        }

        if (!fragmentsToSend.isEmpty()) {
            // partial write
            LOGGER.debug("partial write");
            try {
                establishedContext.interestOps().add(OP_WRITE);
            }
            catch (CancelledKeyException cke) {
                LOGGER
                        .warn(
                                "CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>",
                                cke.getMessage(), establishedContext.socket().getTransportInfo().getPeerAddress(),
                                establishedContext.socket().getTransportInfo().getPeerPort()
                        );
                establishedContext.close();
            }
            return false;
        }
        else {
            return true;
        }
    }

    @Override
    public void run() {
        accept(Collections.emptyList());
    }

    @Override
    public AtomicBoolean needRead() {
        return needRead;
    }
}
