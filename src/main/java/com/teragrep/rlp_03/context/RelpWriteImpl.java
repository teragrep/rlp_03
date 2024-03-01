package com.teragrep.rlp_03.context;

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

public class RelpWriteImpl implements RelpWrite {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpWriteImpl.class);

    private final ConnectionContext connectionContext;

    // TODO rewrite frame object, so it includes also the parser and buffer representation
    private final ConcurrentLinkedQueue<RelpFrameTX> queue;

    private AtomicBoolean sendInProgress;
    private ByteBuffer responseBuffer;
    private Optional<RelpFrameTX> currentResponse;

    // tls
    private final AtomicBoolean needRead;

    RelpWriteImpl(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.sendInProgress = new AtomicBoolean();

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
            if (sendInProgress.compareAndSet(false, true)) {
                while (true) {
                    if (responseBuffer.hasRemaining()) {
                        // resume partial write, this can be removed if txFrames contain their own buffers and partially sent data is kept there
                        if (!sendFrame(Optional.empty())) {
                            // resumed write still incomplete next
                            LOGGER.debug("partial write while resumed write");
                            if (!sendInProgress.compareAndSet(true, false)) {
                                throw new IllegalStateException("logic failure 1");
                            }
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
                            if (!sendInProgress.compareAndSet(true, false)) {
                                throw new IllegalStateException("logic failure 2");
                            }
                            break hasRemaining;
                        }
                    }
                }
                if (!sendInProgress.compareAndSet(true, false)) {
                    throw new IllegalStateException("logic failure 3");
                }
            } else {
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
                connectionContext.close();
                return false;
            }
            ((Buffer) responseBuffer).flip();
        }

        long bytesWritten;
        try {
            bytesWritten = connectionContext.socket().write(new ByteBuffer[]{responseBuffer}); // FIXME
        }
        catch (NeedsReadException nre) {
            needRead.set(true);
            try {
                connectionContext.interestOps().add(OP_READ);
            }
            catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }
        catch (NeedsWriteException nwe) {
            try {
                connectionContext.interestOps().add(OP_WRITE);
            }
            catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }
        catch (IOException ioException) {
            LOGGER.error("IOException <{}> while writing to socket. PeerAddress <{}> PeerPort <{}>", ioException, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
            connectionContext.close();
            return false;
        }

        if (bytesWritten < 0) {
            LOGGER.error("Socket write returns <{}>. Closing connection to  PeerAddress <{}> PeerPort <{}>", bytesWritten, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
            // close connection
            connectionContext.close();
            return false;
        }

        if (bytesWritten < responseBuffer.remaining()) {
            // partial write
            LOGGER.debug("partial write");
            try {
                connectionContext.interestOps().add(OP_WRITE);
            }
            catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }

        if (!responseBuffer.hasRemaining() && currentResponse.isPresent()) {
            LOGGER.debug("complete write");
            if (RelpCommand.SERVER_CLOSE.equals(currentResponse.get().getCommand())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Sent command <{}>, Closing connection to  PeerAddress <{}> PeerPort <{}>", RelpCommand.SERVER_CLOSE, connectionContext.socket().getTransportInfo().getPeerAddress(), connectionContext.socket().getTransportInfo().getPeerPort());
                }
                connectionContext.close();
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
