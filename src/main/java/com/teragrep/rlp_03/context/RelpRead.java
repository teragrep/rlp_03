package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_01.RelpParser;
import com.teragrep.rlp_03.FrameProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_READ;

public class RelpRead implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpRead.class);
    private final ExecutorService executorService;
    private final ConnectionContext connectionContext;
    private final Supplier<FrameProcessor> frameProcessorSupplier;
    private final ByteBuffer readBuffer;
    private final RelpParser relpParser;
    private final Lock lock;

    RelpRead(ExecutorService executorService, ConnectionContext connectionContext, Supplier<FrameProcessor> frameProcessorSupplier) {
        this.executorService = executorService;
        this.connectionContext = connectionContext;
        this.frameProcessorSupplier = frameProcessorSupplier;

        this.readBuffer = ByteBuffer.allocateDirect(512);
        this.readBuffer.flip();

        this.relpParser = new RelpParser();

        this.lock = new ReentrantLock();
    }

    @Override
    public void run() {
        LOGGER.info("relp read before lock");
        lock.lock();
        LOGGER.info("relp read");
        while (!relpParser.isComplete()) {
            if (!readBuffer.hasRemaining()) {
                LOGGER.info("readBuffer has no remaining bytes");
                readBuffer.clear(); // everything read already

                int readBytes = 0;
                try {
                    readBytes = connectionContext.socket.read(readBuffer);
                    LOGGER.info("connectionContext.read got <{}> bytes from socket", readBytes);
                }
                catch (IOException ioException) {
                    LOGGER.error("Exception <{}> while reading from socket. Closing connectionContext <{}>.", ioException.getMessage(), connectionContext.socket.getTransportInfo());
                    // TODO close
                }
                finally {
                    readBuffer.flip();
                }

                if (readBytes == 0) {
                    LOGGER.info("socket need to read more bytes");
                    // socket needs to read more
                    connectionContext.interestOps().add(OP_READ);
                    LOGGER.info("more bytes requested from socket");
                    break;
                }
                else if (readBytes < 0) {
                    LOGGER.info("problem with socket, go away");
                    // close connection
                    try {
                        connectionContext.close();
                    }
                    catch (IOException ioException) {
                        // TODO betterment?
                        LOGGER.warn("unable to close connection");
                    }
                    break;
                }
            }
            else {
                byte b = readBuffer.get();
                relpParser.parse(b);
            }
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("relpParser.isComplete() returning <{}>", relpParser.isComplete());
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

            LOGGER.info("received rxFrame <[{}]>", rxFrame);

            relpParser.reset();
            LOGGER.info("unlocking at frame complete");
            lock.unlock(); // NOTE that things down here are unlocked, use thread-safe ONLY!

            LOGGER.info("submitting next read runnable");
            executorService.submit(this); // next thread comes here
            RelpFrameTX frameTX = frameProcessorSupplier.get().process(rxFrame); // this thread goes there
            connectionContext.relpWrite.accept(frameTX);
            LOGGER.info("processed txFrame. End of thread's processing.");
        }
        else {
            LOGGER.info("unlocking at frame partial");
            lock.unlock();
        }
    }
}
