package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.OP_WRITE;

public class RelpWrite implements Consumer<RelpFrameTX>, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpWrite.class);

    private final ConnectionContext connectionContext;

    // TODO rewrite frame object, so it includes also the parser and buffer representation
    private final ConcurrentLinkedQueue<RelpFrameTX> queue;

    private final Lock lock;

    private ByteBuffer responseBuffer;
    private RelpFrameTX currentResponse;

    RelpWrite(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();

        this.responseBuffer = ByteBuffer.allocateDirect(1024);
        this.currentResponse = null;
    }

    // this must be thread-safe!
    @Override
    public void accept(RelpFrameTX relpFrameTX) {
        LOGGER.debug("Accepting <[{}]>", relpFrameTX);

        // FIXME create stub frame, this is for resumed writes
        if (relpFrameTX != null) {
            queue.add(relpFrameTX);
        }


        while (queue.peek() != null) {
            if (lock.tryLock()) {
                while (true) {
                    RelpFrameTX frameTX = queue.poll();
                    if (frameTX == null) {
                        break;
                    }

                    if (!sendFrame(frameTX)) {
                        // partial write
                        LOGGER.debug("partial write");
                        break;
                    }
                }
                lock.unlock();
            } else {
                break;
            }
        }
    }

    // TODO support resumed writes
    private boolean sendFrame(RelpFrameTX frameTX) {
        LOGGER.debug("sendFrame <{}>", frameTX);
        currentResponse = frameTX;
        int frameLength = frameTX.length();
        ByteBuffer responseBuffer = ByteBuffer.allocateDirect(frameLength);

        // unnecessary try because frameTX.write() does not throw here, see https://github.com/teragrep/rlp_01/issues/61
        try {
            frameTX.write(responseBuffer);
        }
        catch (IOException ioException) {
            LOGGER.error("Exception <{}> while writing frame to buffer", ioException.getMessage());
            connectionContext.close();
            return false;
        }
        responseBuffer.flip();

        int bytesWritten;
        try {
            bytesWritten = connectionContext.socket.write(responseBuffer); // TODO handle partial write
        } catch (IOException ioException) {
            LOGGER.error("Exception <{}> while writing buffer to socket. PeerAddress <{}> PeerPort <{}>", ioException.getMessage(), connectionContext.socket.getTransportInfo().getPeerAddress(), connectionContext.socket.getTransportInfo().getPeerPort());
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
            connectionContext.interestOps().add(OP_WRITE);
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
        // FIXME create stub txFrame
        accept(null);
    }
}
