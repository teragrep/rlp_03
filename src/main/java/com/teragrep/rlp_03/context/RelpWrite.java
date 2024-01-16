package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

public class RelpWrite implements Consumer<List<RelpFrameTX>>, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpWrite.class);

    private final ConnectionContext connectionContext;

    // TODO rewrite frame object, so it includes also the parser and buffer representation
    private final ConcurrentLinkedQueue<RelpFrameTX> queue;

    private final Lock lock;

    private ByteBuffer responseBuffer;
    private RelpFrameTX currentResponse;

    // tls
    public final AtomicBoolean needRead;

    RelpWrite(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();

        this.responseBuffer = ByteBuffer.allocateDirect(0);
        this.currentResponse = null;

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
                        if (!sendFrame(null)) {
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
                        if (!sendFrame(frameTX)) {
                            // partial write
                            LOGGER.debug("partial write while new write");
                            lock.unlock();
                            break hasRemaining;
                        }
                    }
                }
                lock.unlock();
            } else {
                break;
            }
        }
    }

    private boolean sendFrame(RelpFrameTX frameTX) {
        LOGGER.trace("sendFrame <{}>", frameTX);

        // TODO create stub txFrame, null is bad
        if (frameTX != null) {

            if (responseBuffer.hasRemaining()) {
                IllegalStateException ise = new IllegalStateException("partial write exists while attempting new one");
                LOGGER.error("IllegalStateException in sendFrame <{}>", ise.getMessage());
                throw ise;
            }

            currentResponse = frameTX;
            int frameLength = frameTX.length();
            responseBuffer = ByteBuffer.allocateDirect(frameLength);

            // unnecessary try because frameTX.write() does not throw here, see https://github.com/teragrep/rlp_01/issues/61
            try {
                frameTX.write(responseBuffer);
            }
            catch (IOException ioException) {
                // safe guard here, remove after https://github.com/teragrep/rlp_01/issues/61
                LOGGER.error("Exception <{}> while writing frame to buffer", ioException.getMessage());
                connectionContext.close();
                return false;
            }
            responseBuffer.flip();
        }

        int bytesWritten;
        try {
            bytesWritten = connectionContext.socket.write(responseBuffer);
        }
        catch (NeedsReadException nre) {
            needRead.set(true);
            try {
                connectionContext.interestOps().add(OP_READ);
            }
            catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }
        catch (NeedsWriteException nwe) {
            try {
                connectionContext.interestOps().add(OP_WRITE);
            }
            catch (CancelledKeyException cke) {
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }
        catch (IOException ioException) {
            LOGGER.error("Exception <{}> while writing to socket. PeerAddress <{}> PeerPort <{}>", ioException.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
            connectionContext.close();
            return false;
        }

        if (bytesWritten < 0) {
            LOGGER.error("Socket write returns <{}>. Closing connection to  PeerAddress <{}> PeerPort <{}>", bytesWritten, connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
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
                LOGGER.warn("CancelledKeyException <{}>. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                connectionContext.close();
            }
            return false;
        }

        if (!responseBuffer.hasRemaining()) {
            LOGGER.debug("complete write");
            if (RelpCommand.SERVER_CLOSE.equals(currentResponse.getCommand())) {
                LOGGER.debug("Sent command <{}>, Closing connection to  PeerAddress <{}> PeerPort <{}>", RelpCommand.SERVER_CLOSE, connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
                connectionContext.close();
            }
        }

        return true;
    }

    @Override
    public void run() {
        accept(Collections.emptyList());
    }
}
