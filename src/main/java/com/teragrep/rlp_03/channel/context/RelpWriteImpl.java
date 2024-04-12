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

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

final class RelpWriteImpl implements RelpWrite {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpWriteImpl.class);

    private final EstablishedContext establishedContext;

    // TODO rewrite frame object, so it includes also the parser and buffer representation
    private final ConcurrentLinkedQueue<RelpFrameTX> queue;

    private final Lock lock;

    private ByteBuffer responseBuffer;
    private Optional<RelpFrameTX> currentResponse;

    // tls
    private final AtomicBoolean needRead;

    RelpWriteImpl(EstablishedContext establishedContext) {
        this.establishedContext = establishedContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();

        this.responseBuffer = ByteBuffer.allocateDirect(0);
        this.currentResponse = Optional.empty();

        this.needRead = new AtomicBoolean();
    }

    // this must be thread-safe!
    @Override
    public void accept(List<RelpFrameTX> relpFrameTXList) {
        LOGGER.trace("Accepting <[{}]>", relpFrameTXList);

        // FIXME create stub RelpFrameTX, this is for resumed writes
        if (!relpFrameTXList.isEmpty()) {
            queue.addAll(relpFrameTXList);
        }

        hasRemaining:
        while (queue.peek() != null || responseBuffer.hasRemaining()) {
            if (lock.tryLock()) {
                while (true) {
                    if (responseBuffer.hasRemaining()) {
                        // resume partial write, this can be removed if txFrames contain their own buffers and partially sent data is kept there
                        if (!sendFrame(Optional.empty())) {
                            // resumed write still incomplete next
                            LOGGER.debug("partial write while resumed write");
                            lock.unlock();
                            break hasRemaining;
                        }
                    }
                    else {
                        RelpFrameTX frameTX = queue.poll();
                        if (frameTX == null) {
                            break;
                        }
                        if (!sendFrame(Optional.of(frameTX))) {
                            // partial write
                            LOGGER.debug("partial write while new write");
                            lock.unlock();
                            break hasRemaining;
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

    private boolean sendFrame(Optional<RelpFrameTX> frameTXOptional) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("sendFrame frameTXOptional.isPresent() <{}>", frameTXOptional.isPresent());
        }

        // TODO create stub txFrame, null is bad
        if (frameTXOptional.isPresent()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("sendFrame <{}>", frameTXOptional.get());
            }

            if (responseBuffer.hasRemaining()) {
                IllegalStateException ise = new IllegalStateException("partial write exists while attempting new one");
                LOGGER.error("IllegalStateException in sendFrame <{}>", ise.getMessage());
                throw ise;
            }

            currentResponse = Optional.of(frameTXOptional.get());
            int frameLength = currentResponse.get().length();
            responseBuffer = ByteBuffer.allocateDirect(frameLength);

            // unnecessary try because frameTX.write() does not throw here, see https://github.com/teragrep/rlp_01/issues/61
            try {
                currentResponse.get().write(responseBuffer);
            }
            catch (IOException ioException) {
                // safe guard here, remove after https://github.com/teragrep/rlp_01/issues/61
                LOGGER.error("IOException <{}> while writing frame to buffer", ioException.toString());
                establishedContext.close();
                return false;
            }
            ((Buffer) responseBuffer).flip();
        }

        long bytesWritten;
        try {
            bytesWritten = establishedContext.socket().write(new ByteBuffer[] {
                    responseBuffer
            }); // FIXME
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

        if (bytesWritten < responseBuffer.remaining()) {
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

        if (!responseBuffer.hasRemaining() && currentResponse.isPresent()) {
            LOGGER.debug("complete write");
            if (RelpCommand.SERVER_CLOSE.equals(currentResponse.get().getCommand())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER
                            .debug(
                                    "Sent command <{}>, Closing connection to  PeerAddress <{}> PeerPort <{}>",
                                    RelpCommand.SERVER_CLOSE,
                                    establishedContext.socket().getTransportInfo().getPeerAddress(),
                                    establishedContext.socket().getTransportInfo().getPeerPort()
                            );
                }
                establishedContext.close();
            }
        }

        return true;
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
