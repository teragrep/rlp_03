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

package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.FrameProcessor;
import com.teragrep.rlp_03.FrameProcessorPool;
import com.teragrep.rlp_03.context.buffer.BufferLease;
import com.teragrep.rlp_03.context.buffer.BufferLeasePool;
import com.teragrep.rlp_03.context.frame.RelpFrameAccess;
import com.teragrep.rlp_03.context.frame.RelpFrameImpl;
import com.teragrep.rlp_03.context.frame.RelpFrameLeaseful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

public class RelpReadImpl implements RelpRead {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpReadImpl.class);
    private final ExecutorService executorService;
    private final ConnectionContextImpl connectionContext;
    private final FrameProcessorPool frameProcessorPool;
    private final BufferLeasePool bufferLeasePool;
    private final List<RelpFrameLeaseful> relpFrames;
    private final LinkedList<BufferLease> activeBuffers;
    private final Lock lock;
    // tls
    public final AtomicBoolean needWrite;

    RelpReadImpl(ExecutorService executorService, ConnectionContextImpl connectionContext, FrameProcessorPool frameProcessorPool, BufferLeasePool bufferLeasePool) {
        this.executorService = executorService;
        this.connectionContext = connectionContext;
        this.frameProcessorPool = frameProcessorPool;
        this.bufferLeasePool = bufferLeasePool;

        this.relpFrames = new ArrayList<>(1);
        this.activeBuffers = new LinkedList<>();
        this.lock = new ReentrantLock();
        this.needWrite = new AtomicBoolean();
    }

    @Override
    public void run() {
        try {
            LOGGER.debug("task entry!");
            lock.lock();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("task lock! with activeBuffers.size() <{}>", activeBuffers.size());
            }

            // FIXME this is quite stateful
            RelpFrameLeaseful relpFrame;
            if (relpFrames.isEmpty()) {
                relpFrame = new RelpFrameLeaseful(new RelpFrameImpl());
            } else {
                relpFrame = relpFrames.remove(0);
            }

            boolean complete = false;
            // resume if frame is present
            if (!activeBuffers.isEmpty()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("resuming buffer <{}>, activeBuffers <{}>", activeBuffers.get(0), activeBuffers);
                }
                complete = innerLoop(relpFrame, true);
            }

            while (activeBuffers.isEmpty() && !complete) {
                // fill buffers for read
                long readBytes = readData();

                if (readBytesToOperation(readBytes)) {
                    LOGGER.debug("readBytesToOperation(readBytes) forces return");
                    relpFrames.add(relpFrame); // back to list, as incomplete it is
                    lock.unlock(); // FIXME, use finally and single point of return
                    return;
                }

                if (innerLoop(relpFrame, false)) {
                    break;
                }

            }

            if (relpFrame.endOfTransfer().isComplete()) {
                LOGGER.trace("received relpFrame <[{}]>", relpFrame);

                LOGGER.debug("unlocking at frame complete, activeBuffers <{}>", activeBuffers);
                lock.unlock();
                // NOTE that things down here are unlocked, use thread-safe ONLY!
                processFrame(relpFrame);
            } else {
                relpFrames.add(relpFrame); // back to list, as incomplete it is
                LOGGER.debug("unlocking at frame partial, activeBuffers <{}>", activeBuffers);
                lock.unlock();
            }
            LOGGER.debug("task done!");
        } catch (Throwable t) {
            LOGGER.error("run() threw", t);
            throw t;
        }
    }

    private boolean innerLoop(RelpFrameLeaseful relpFrame, boolean hasRef) {
        boolean rv = false;
        while (!activeBuffers.isEmpty()) {
            // TODO redesign this, very coupled design here !
            BufferLease buffer = activeBuffers.removeFirst();
            LOGGER.debug("submitting buffer <{}> from activeBuffers <{}> to relpFrame", buffer, activeBuffers);

            if (relpFrame.submit(buffer)) {
                rv = true;

                if (buffer.buffer().hasRemaining()) {
                    buffer.addRef(); // a shared buffer

                    // return back as it has some remaining
                    activeBuffers.push(buffer);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("buffer.buffer <{}>, buffer.buffer().hasRemaining() <{}> returned it to activeBuffers <{}>", buffer.buffer(), buffer.buffer().hasRemaining(), activeBuffers);
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
                connectionContext.interestOps().add(OP_READ);
            } catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            LOGGER.debug("more bytes requested from socket");
            return true;
        } else if (readBytes < 0) {
            LOGGER.warn("socket.read returned <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", readBytes, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
            // close connection
            connectionContext.close();
            return true;
        }
        return false;
    }

    private void processFrame(RelpFrameLeaseful relpFrame) {
        // NOTE use thread-safe ONLY!

        if (!RelpCommand.CLOSE.equals(relpFrame.command().toString())) {
            try {
                LOGGER.debug("submitting next read runnable");
                executorService.execute(this); // next thread comes here
            } catch (RejectedExecutionException ree) {
                LOGGER.error("executorService.execute threw <{}>", ree.getMessage());
                throw ree;
            }
        } else {
            LOGGER.debug("close requested, not submitting next read runnable");
        }

        RelpFrameAccess frameAccess = new RelpFrameAccess(relpFrame);

        FrameProcessor frameProcessor = frameProcessorPool.take(); // FIXME should this be locked to ensure visibility


        if (!frameProcessor.isStub()) {
            frameProcessor.accept(new FrameContext(connectionContext, frameAccess)); // this thread goes there
            frameProcessorPool.offer(frameProcessor);
        } else {
            // TODO should this be IllegalState or should it just '0 serverclose 0' ?
            LOGGER.warn("FrameProcessorPool closing, rejecting frame and closing connection for PeerAddress <{}> PeerPort <{}>", connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
            connectionContext.close();
        }

        // terminate access
        frameAccess.access().terminate();

        // return buffers
        List<BufferLease> leases = relpFrame.release();
        for (BufferLease bufferLease : leases) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("releasing id <{}> with refs <{}>", bufferLease.id(), bufferLease.refs());
            }
            bufferLeasePool.offer(bufferLease);
        }

        LOGGER.debug("processed txFrame. End of thread's processing.");
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

            readBytes = connectionContext.socket().read(byteBufferArray);

            activateBuffers(bufferLeases);

            LOGGER.debug("connectionContext.read got <{}> bytes from socket", readBytes);
        } catch (NeedsReadException nre) {
            try {
                connectionContext.interestOps().add(OP_READ);
            } catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
        } catch (NeedsWriteException nwe) {
            needWrite.set(true);
            try {
                connectionContext.interestOps().add(OP_WRITE);
            } catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
        } catch (IOException ioException) {
            LOGGER.error("IOException <{}> while reading from socket. Closing connectionContext PeerAddress <{}> PeerPort <{}>.", ioException, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
            connectionContext.close();
        }

        return readBytes;
    }

    private void activateBuffers(List<BufferLease> bufferLeases) {
        for (BufferLease bufferLease : bufferLeases) {
            if (bufferLease.buffer().position() != 0) {
                bufferLease.buffer().flip();
                activeBuffers.add(bufferLease);
            } else {
                // unused buffer, releasing back to pool
                bufferLeasePool.offer(bufferLease);
            }
        }
    }

    public AtomicBoolean needWrite() {
        return needWrite;
    }
}
