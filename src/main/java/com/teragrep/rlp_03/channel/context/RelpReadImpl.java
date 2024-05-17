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

import com.teragrep.rlp_03.frame.*;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_03.channel.buffer.BufferLease;
import com.teragrep.rlp_03.channel.buffer.BufferLeasePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

final class RelpReadImpl implements RelpRead {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpReadImpl.class);
    private final EstablishedContextImpl establishedContext;
    private final FrameDelegate frameDelegate;
    private final BufferLeasePool bufferLeasePool;
    private final FrameClockLeaseful frameClockLeaseful;
    private final RelpFrameStub relpFrameStub;
    private final LinkedList<BufferLease> activeBuffers;
    private final Lock lock;
    // tls
    public final AtomicBoolean needWrite;

    RelpReadImpl(
            EstablishedContextImpl establishedContext,
            FrameDelegate frameDelegate,
            BufferLeasePool bufferLeasePool
    ) {
        this.establishedContext = establishedContext;
        this.frameDelegate = frameDelegate;
        this.bufferLeasePool = bufferLeasePool;

        this.frameClockLeaseful = new FrameClockLeaseful(bufferLeasePool, new FrameClock());
        this.relpFrameStub = new RelpFrameStub();
        this.activeBuffers = new LinkedList<>();
        this.lock = new ReentrantLock();
        this.needWrite = new AtomicBoolean();
    }

    @Override
    public void run() {
        LOGGER.debug("run entry!");
        lock.lock();
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("run lock! with activeBuffers.size() <{}>", activeBuffers.size());
            }
            while (true) {
                LOGGER.debug("run loop start");

                RelpFrame frame = relpFrameStub;
                if (!activeBuffers.isEmpty()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("resuming buffer <{}>, activeBuffers <{}>", activeBuffers.get(0), activeBuffers);
                    }
                    frame = attemptFrameCompletion();
                }

                while (activeBuffers.isEmpty() && frame.isStub()) {
                    // fill buffers for read
                    long readBytes = readData();

                    if (readBytesToOperation(readBytes)) {
                        LOGGER.debug("readBytesToOperation(readBytes) forces return");
                        return; // TODO this is quite ugly return, single point of return is preferred!
                    }

                    frame = attemptFrameCompletion();
                    if (!frame.isStub()) {
                        break;
                    }

                }

                if (!frame.isStub()) {
                    LOGGER.trace("received relpFrame <[{}]>", frame);
                    LOGGER.debug("frame complete, activeBuffers <{}>", activeBuffers);
                    if (!delegateFrame(frame)) {
                        break;
                    }
                }
                else {
                    LOGGER.debug("frame partial, activeBuffers <{}>", activeBuffers);
                }
                LOGGER.debug("loop done!");
            }
        }
        catch (Throwable t) {
            LOGGER.error("run() threw", t);
            throw t;
        }
        finally {
            lock.unlock();
        }
    }

    private RelpFrame attemptFrameCompletion() {
        RelpFrame rv = relpFrameStub;
        while (!activeBuffers.isEmpty()) {
            // TODO redesign this, very coupled design here !
            BufferLease buffer = activeBuffers.removeFirst();
            LOGGER.debug("submitting buffer <{}> from activeBuffers <{}> to relpFrame", buffer, activeBuffers);

            rv = frameClockLeaseful.submit(buffer);
            if (!rv.isStub()) {
                if (buffer.buffer().hasRemaining()) {
                    buffer.addRef(); // a shared buffer

                    // return back as it has some remaining
                    activeBuffers.push(buffer);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER
                                .debug(
                                        "buffer.buffer <{}>, buffer.buffer().hasRemaining() <{}> returned it to activeBuffers <{}>",
                                        buffer.buffer(), buffer.buffer().hasRemaining(), activeBuffers
                                );
                    }
                }
                break;
            }
        }
        return rv;
    }

    private boolean readBytesToOperation(long readBytes) {
        if (readBytes == 0) {
            // socket needs to read more
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
            LOGGER.debug("more bytes requested from socket");
            return true;
        }
        else if (readBytes < 0) {
            LOGGER
                    .warn(
                            "socket.read returned <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>",
                            readBytes, establishedContext.socket().getTransportInfo().getPeerAddress(),
                            establishedContext.socket().getTransportInfo().getPeerPort()
                    );
            // close connection
            establishedContext.close();
            return true;
        }
        return false;
    }

    private boolean delegateFrame(RelpFrame relpFrame) {
        boolean rv;

        RelpFrameAccess relpFrameAccess = new RelpFrameAccess(relpFrame);
        FrameContext frameContext = new FrameContext(establishedContext, relpFrameAccess);

        rv = frameDelegate.accept(frameContext);

        LOGGER.debug("processed txFrame.");
        return rv;
    }

    private long readData() {
        long readBytes = 0;
        try {
            List<BufferLease> bufferLeases = bufferLeasePool.take(4);

            List<ByteBuffer> byteBufferList = new LinkedList<>();
            for (BufferLease bufferLease : bufferLeases) {
                if (bufferLease.isStub()) {
                    continue;
                }
                byteBufferList.add(bufferLease.buffer());
            }
            ByteBuffer[] byteBufferArray = byteBufferList.toArray(new ByteBuffer[0]);

            readBytes = establishedContext.socket().read(byteBufferArray);

            activateBuffers(bufferLeases);

            LOGGER.debug("establishedContext.read got <{}> bytes from socket", readBytes);
        }
        catch (NeedsReadException nre) {
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
        }
        catch (NeedsWriteException nwe) {
            needWrite.set(true);
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
        }
        catch (IOException ioException) {
            LOGGER
                    .error(
                            "IOException <{}> while reading from socket. Closing establishedContext PeerAddress <{}> PeerPort <{}>.",
                            ioException, establishedContext.socket().getTransportInfo().getPeerAddress(),
                            establishedContext.socket().getTransportInfo().getPeerPort()
                    );
            establishedContext.close();
        }

        return readBytes;
    }

    private void activateBuffers(List<BufferLease> bufferLeases) {
        for (BufferLease bufferLease : bufferLeases) {
            if (bufferLease.buffer().position() != 0) {
                bufferLease.buffer().flip();
                activeBuffers.add(bufferLease);
            }
            else {
                // unused buffer, releasing back to pool
                bufferLease.removeRef();
            }
        }
    }

    public AtomicBoolean needWrite() {
        return needWrite;
    }
}
