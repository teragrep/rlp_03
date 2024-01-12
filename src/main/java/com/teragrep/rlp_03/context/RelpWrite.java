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
        LOGGER.info("Accepting <[{}]>", relpFrameTX);

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
                        break;
                    }
                }
                lock.unlock();
            }
            else {
                break;
            }
        }
    }

    // TODO support resumed writes
    private boolean sendFrame(RelpFrameTX frameTX) {
        LOGGER.info("sendFrame <{}>", frameTX);
        currentResponse = frameTX;
        int frameLength = frameTX.length();
        ByteBuffer responseBuffer = ByteBuffer.allocateDirect(frameLength);

        try {
            frameTX.write(responseBuffer);
            responseBuffer.flip();

            int bytesWritten = connectionContext.socket.write(responseBuffer); // TODO handle partial write

            if (bytesWritten < 0) {
                LOGGER.info("problem with socket, go away");
                // close connection
                try {
                    connectionContext.close();
                }
                catch (IOException ioException) {
                    // TODO betterment?
                    LOGGER.warn("unable to close connection");
                }
                return false;
            }

            if (bytesWritten < responseBuffer.remaining()) {
                // partial write
                LOGGER.info("partial write");
                connectionContext.interestOps().add(OP_WRITE);
                return false;
            }

            if (!responseBuffer.hasRemaining()) {
                LOGGER.info("complete write");
                if (RelpCommand.SERVER_CLOSE.equals(currentResponse.getCommand())) {
                    LOGGER.info("Sent command <{}>, closing connection.", RelpCommand.SERVER_CLOSE);
                    connectionContext.close();
                }
            }
        }
        catch (IOException ioException) {
            LOGGER.error("Exception <{}> while writing frame to buffer", ioException.getMessage());
            try {
                connectionContext.close();
            } catch (IOException e) {
                LOGGER.warn("Exception <{}> while closing connection", e.getMessage());
            }
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        // FIXME create stub txFrame
        accept(null);
    }
}
