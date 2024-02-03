package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.FrameProcessor;
import com.teragrep.rlp_03.context.buffer.BufferLease;
import com.teragrep.rlp_03.context.buffer.BufferPool;
import com.teragrep.rlp_03.context.frame.RelpFrame;
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
    private final FrameProcessor frameProcessor;
    private final BufferPool bufferPool;
    private final List<RelpFrameLeaseful> relpFrames;
    private final LinkedList<BufferLease> activeBuffers;
    private final Lock lock;

    // tls
    public final AtomicBoolean needWrite;

    RelpReadImpl(ExecutorService executorService, ConnectionContextImpl connectionContext, FrameProcessor frameProcessor) {
        this.executorService = executorService;
        this.connectionContext = connectionContext;
        this.frameProcessor = frameProcessor;


        this.bufferPool = new BufferPool();


        this.relpFrames = new ArrayList<>(1);

        this.activeBuffers = new LinkedList<>();

        this.lock = new ReentrantLock();

        this.needWrite = new AtomicBoolean();
    }

    @Override
    public void run() {
        LOGGER.info("task entry!");
        lock.lock();
        LOGGER.info("task lock!");

        // FIXME this is quite stateful
        RelpFrameLeaseful relpFrame;
        if (relpFrames.isEmpty()) {
            relpFrame = new RelpFrameLeaseful(new RelpFrameImpl());
        }
        else {
            relpFrame = relpFrames.remove(0);
        }


upper:
        while (activeBuffers.isEmpty()) {
            LOGGER.info("activeBuffers.isEmpty() <{}>", activeBuffers.isEmpty());
            // fill buffers for read
            long readBytes = readData();

            if (readBytesToOperation(readBytes)) {
                LOGGER.info("readBytesToOperation(readBytes) forces return");
                relpFrames.add(relpFrame); // back to list, as incomplete it is
                lock.unlock(); // FIXME, use finally?
                return;
            }

            while (!activeBuffers.isEmpty()) {
                // TODO redesign this, very coupled design here !
                BufferLease buffer = activeBuffers.removeFirst();
                LOGGER.info("submitting buffer <{}> from activeBuffers <{}> to relpFrame", buffer, activeBuffers);

                if (relpFrame.submit(buffer)) { // TODO use relpFrameWithLeases which tracks which buffers it has leased
                    if (buffer.buffer().hasRemaining()) {
                        LOGGER.info("buffer.buffer().hasRemaining() <{}> returning it", buffer.buffer().hasRemaining());
                        // return back as it has some remaining
                        //activeBuffers.add(buffer);
                    }
                    break upper;
                }
            }
        }

        if (relpFrame.endOfTransfer().isComplete()) {
            LOGGER.info("frame complete");
            LOGGER.trace("received relpFrame <[{}]>", relpFrame);

            LOGGER.debug("unlocking at frame complete");
            lock.unlock();
            // NOTE that things down here are unlocked, use thread-safe ONLY!
            processFrame(relpFrame);
        } else {
            relpFrames.add(relpFrame); // back to list, as incomplete it is
            LOGGER.info("unlocking at frame partial");
            lock.unlock();
        }
        LOGGER.info("task done!");
    }

    private boolean readBytesToOperation(long readBytes) {
        if (readBytes == 0) {
            LOGGER.debug("socket need to read more bytes");
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
            }
        } else {
            LOGGER.debug("close requested, not submitting next read runnable");
        }

        RelpFrameAccess frameAccess = new RelpFrameAccess(relpFrame);
        frameProcessor.accept(new FrameContext(connectionContext, frameAccess)); // this thread goes there

        // terminate access
        frameAccess.access().terminate();

        // return buffers
        Set<BufferLease> leaseSet = relpFrame.release();
        for (BufferLease bufferLease : leaseSet) {
            bufferLease.removeRef();
            bufferPool.offer(bufferLease);
        }

        LOGGER.debug("processed txFrame. End of thread's processing.");
    }

    private long readData() {
        long readBytes = -1;
        try {
            ByteBuffer[] buffers = bufferPool.take(4); // TODO use BufferPool
            readBytes = connectionContext.socket().read(buffers);

            activateBuffers(buffers);

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

    private void activateBuffers(ByteBuffer[] buffers) {
        LOGGER.info("activatingBuffers <{}>", (Object) buffers);
        for (ByteBuffer slotBuffer : buffers) {
            if (slotBuffer.position() != 0) {
                LOGGER.info("adding slotBuffer <{}> due to position != 0", slotBuffer);
                slotBuffer.flip();
                activeBuffers.add(new BufferLease(slotBuffer));
            } else {
                // unused, FIXME new BufferLease for nothing
                LOGGER.info("releasing to bufferPool slotBuffer <{}> due to position == 0", slotBuffer);
                bufferPool.offer(new BufferLease(slotBuffer));
            }
        }
        LOGGER.info("activateBuffers complete");
    }

    public AtomicBoolean needWrite() {
        return needWrite;
    }
}
