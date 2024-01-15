package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_01.RelpParser;
import com.teragrep.rlp_03.FrameProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

public class RelpRead implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpRead.class);
    private final ExecutorService executorService;
    private final ConnectionContext connectionContext;
    private final FrameProcessorPool frameProcessorPool;
    private final ByteBuffer readBuffer;
    private final RelpParser relpParser;
    private final Lock lock;

    // tls
    public final AtomicBoolean needWrite;

    RelpRead(ExecutorService executorService, ConnectionContext connectionContext, FrameProcessorPool frameProcessorPool) {
        this.executorService = executorService;
        this.connectionContext = connectionContext;
        this.frameProcessorPool = frameProcessorPool;

        this.readBuffer = ByteBuffer.allocateDirect(512);
        this.readBuffer.flip();

        this.relpParser = new RelpParser();

        this.lock = new ReentrantLock();

        this.needWrite = new AtomicBoolean();
    }

    @Override
    public void run() {
        LOGGER.debug("relp read before lock");
        lock.lock();
        LOGGER.debug("relp read");
        while (!relpParser.isComplete()) {
            if (!readBuffer.hasRemaining()) {
                LOGGER.debug("readBuffer has no remaining bytes");
                readBuffer.clear(); // everything read already

                int readBytes = 0;
                try {
                    readBytes = connectionContext.socket.read(readBuffer);
                    LOGGER.debug("connectionContext.read got <{}> bytes from socket", readBytes);
                }
                catch (NeedsReadException nre) {
                    connectionContext.interestOps().add(OP_READ);
                    break;
                }
                catch (NeedsWriteException nwe) {
                    needWrite.set(true);
                    connectionContext.interestOps().add(OP_WRITE);
                    break;
                }
                catch (IOException ioException) {
                    LOGGER.error("Exception <{}> while reading from socket. Closing connectionContext PeerAddress <{}> PeerPort <{}>.", ioException.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                    connectionContext.close();
                    break;
                } finally {
                    readBuffer.flip();
                }

                if (readBytes == 0) {
                    LOGGER.debug("socket need to read more bytes");
                    // socket needs to read more
                    connectionContext.interestOps().add(OP_READ);
                    LOGGER.debug("more bytes requested from socket");
                    break;
                } else if (readBytes < 0) {
                    LOGGER.debug("problem with socket, go away");
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
            // TODO add TxID checker that they increase monotonically

            final RelpFrameServerRX rxFrame = new RelpFrameServerRX(
                    relpParser.getTxnId(),
                    relpParser.getCommandString(),
                    relpParser.getLength(),
                    relpParser.getData(),
                    connectionContext.socket.getTransportInfo()
            );

            LOGGER.debug("received rxFrame <[{}]>", rxFrame);

            relpParser.reset();
            LOGGER.debug("unlocking at frame complete");
            lock.unlock(); // NOTE that things down here are unlocked, use thread-safe ONLY!

            LOGGER.debug("submitting next read runnable");
            executorService.submit(this); // next thread comes here
            FrameProcessor frameProcessor = frameProcessorPool.take();

            if (!frameProcessor.isStub()) {
                RelpFrameTX frameTX = frameProcessor.process(rxFrame); // this thread goes there
                frameProcessorPool.offer(frameProcessor);
                connectionContext.relpWrite.accept(Collections.singletonList(frameTX));
            }
            else {
                // TODO should this be IllegalState or should it just '0 serverclose 0' ?
                LOGGER.warn("FrameProcessorPool closing, rejecting frame and closing connection for PeerAddress <{}> PeerPort <{}>", connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                connectionContext.close();
            }

            LOGGER.debug("processed txFrame. End of thread's processing.");
        } else {
            LOGGER.debug("unlocking at frame partial");
            lock.unlock();
        }
    }
}
