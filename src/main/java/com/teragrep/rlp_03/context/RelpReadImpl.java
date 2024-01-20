package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpParser;
import com.teragrep.rlp_03.FrameProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
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
    private final ByteBuffer readBuffer; // FIXME refactor this
    private final RelpParser relpParser;
    private final Lock lock;

    // tls
    public final AtomicBoolean needWrite;

    RelpReadImpl(ExecutorService executorService, ConnectionContextImpl connectionContext, FrameProcessor frameProcessor) {
        this.executorService = executorService;
        this.connectionContext = connectionContext;
        this.frameProcessor = frameProcessor;


        this.readBuffer = ByteBuffer.allocateDirect(512);
        this.readBuffer.flip();


        this.relpParser = new RelpParser();

        this.lock = new ReentrantLock();

        this.needWrite = new AtomicBoolean();
    }

    @Override
    public void run() {
        LOGGER.debug("task entry!");
        lock.lock();
        LOGGER.debug("task lock!");
        while (!relpParser.isComplete()) {
            if (!readBuffer.hasRemaining()) {
                LOGGER.debug("readBuffer has no remaining bytes");
                readBuffer.clear(); // everything read already

                long readBytes = 0;
                try {
                    ByteBuffer[] buffers = {readBuffer}; // TODO use BufferPool
                    readBytes = connectionContext.socket().read(buffers);
                    LOGGER.debug("connectionContext.read got <{}> bytes from socket", readBytes);
                }
                catch (NeedsReadException nre) {
                    try {
                        connectionContext.interestOps().add(OP_READ);
                    }
                    catch (CancelledKeyException cke) {
                        LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                        connectionContext.close();
                    }
                    break;
                }
                catch (NeedsWriteException nwe) {
                    needWrite.set(true);
                    try {
                        connectionContext.interestOps().add(OP_WRITE);
                    }
                    catch (CancelledKeyException cke) {
                        LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                        connectionContext.close();
                    }
                    break;
                }
                catch (IOException ioException) {
                    LOGGER.error("IOException <{}> while reading from socket. Closing connectionContext PeerAddress <{}> PeerPort <{}>.", ioException, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                    connectionContext.close();
                    break;
                } finally {
                    readBuffer.flip();
                }

                if (readBytes == 0) {
                    LOGGER.debug("socket need to read more bytes");
                    // socket needs to read more
                    try {
                        connectionContext.interestOps().add(OP_READ);
                    }
                    catch (CancelledKeyException cke) {
                        LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                        connectionContext.close();
                    }
                    LOGGER.debug("more bytes requested from socket");
                    break;
                } else if (readBytes < 0) {
                    LOGGER.warn("socket.read returned <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", readBytes, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                    // close connection
                    connectionContext.close();
                    break;
                }
            } else {
                byte b = readBuffer.get();
                relpParser.parse(b);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("relpParser.isComplete() returning <{}>", relpParser.isComplete());
        }

        if (relpParser.isComplete()) {

            final RelpFrameServerRX rxFrame = new RelpFrameServerRX(
                    relpParser.getTxnId(),
                    relpParser.getCommandString(),
                    relpParser.getLength(),
                    relpParser.getData(),
                    connectionContext
            );

            LOGGER.trace("received rxFrame <[{}]>", rxFrame);

            relpParser.reset();
            LOGGER.debug("unlocking at frame complete");
            lock.unlock();

            // NOTE that things down here are unlocked, use thread-safe ONLY!

            if (!RelpCommand.CLOSE.equals(rxFrame.getCommand())) {
                try {
                    LOGGER.debug("submitting next read runnable");
                    executorService.execute(this); // next thread comes here
                } catch (RejectedExecutionException ree) {
                    LOGGER.error("executorService.execute threw <{}>", ree.getMessage());
                }
            }
            else {
                LOGGER.debug("close requested, not submitting next read runnable");
            }

            frameProcessor.accept(rxFrame); // this thread goes there
            LOGGER.debug("processed txFrame. End of thread's processing.");
        } else {
            LOGGER.debug("unlocking at frame partial");
            lock.unlock();
        }
        LOGGER.debug("task done!");
    }

    public AtomicBoolean needWrite() {
        return needWrite;
    }
}
