package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.TxID;
import com.teragrep.rlp_03.ConnectionOperation;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

public class ReadTask implements Runnable {

    private final ExecutorService executorService;
    private final TxID txID;

    private final ByteBuffer readBuffer;

    ReadTask(ExecutorService executorService, ByteBuffer readBuffer) {
        this.executorService = executorService;
        this.readBuffer = readBuffer;
        this.txID = new TxID();
    }

    @Override
    public void run() {

        ContentTask contentTask = new ContentTask();
        executorService.submit(contentTask);
    }

    ConnectionOperation readRequest() throws IOException {



        int readBytes = connectionContext.read(readBuffer);

        while (readBytes > 0) {
            readBuffer.flip(); // for reading
            while (readBuffer.hasRemaining()) {
                relpParser.parse(readBuffer.get());
                if (relpParser.isComplete()) {

                    RelpFrameServerRX rxFrame = new RelpFrameServerRX(
                            relpParser.getTxnId(),
                            relpParser.getCommandString(),
                            relpParser.getLength(),
                            relpParser.getData(),
                            connectionContext.getTransportInfo()
                    );


                    forkJoinPool.execute(() -> {
                        txDeque.add(frameProcessor.process(rxFrame));
                    });

                    // reset parser state
                    relpParser.reset();
                }
            }
            readBuffer.compact();
            readBuffer.flip(); // for writing
            try {
                // read until there is no more data available
                readBytes = connectionContext.read(readBuffer);
            }
            catch (NeedsReadException | NeedsWriteException tlsException) {
                break;
            }
        }
        if (readBytes < 0) {
            // problem with socket, closing
            return ConnectionOperation.CLOSE;
        }
        else {
            return ConnectionOperation.READ;
        }
    }
}
