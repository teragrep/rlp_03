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

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.context.channel.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * A per connection object that handles reading and writing messages from and to
 * the SocketChannel.
 */
public class ConnectionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionContext.class);

    private InterestOps interestOps;
    private final ExecutorService executorService;
    final Socket socket;
    private final RelpRead relpRead;
    final RelpWrite relpWrite;
    private final FrameProcessorPool frameProcessorPool;

    public ConnectionContext(ExecutorService executorService, Socket socket, Supplier<FrameProcessor> frameProcessorSupplier) {
        this.interestOps = new InterestOpsStub();
        this.executorService = executorService;
        this.socket = socket;
        this.frameProcessorPool = new FrameProcessorPool(frameProcessorSupplier);
        this.relpRead = new RelpRead(executorService, this, frameProcessorPool);
        this.relpWrite = new RelpWrite(this);
    }

    public void updateInterestOps(InterestOps interestOps) {
        this.interestOps = interestOps;
    }

    public void close() {
        try {
            interestOps.removeAll();
        }
        catch (CancelledKeyException cke) {
            LOGGER.warn("CancelledKeyException <{}> in close", cke.getMessage());
        }

        frameProcessorPool.close();

        try {
            socket.close();
        }
        catch (IOException ioe) {
            LOGGER.warn("IOException <{}> in close", ioe.getMessage());
        }
    }


    public void handleEvent(SelectionKey selectionKey) {

        if (!socket.getTransportInfo().getEncryptionInfo().isEncrypted()) {
            // plain connection, reads are reads, writes are writes

            if (selectionKey.isReadable()) { // perhaps track read/write needs here per direction too
                LOGGER.debug("handleEvent taking read");
                interestOps.remove(OP_READ);
                LOGGER.debug("handleEvent submitting new runnable for read");
                executorService.submit(relpRead);
                LOGGER.debug("handleEvent exiting read");
            }

            if (selectionKey.isWritable()) {
                LOGGER.debug("handleEvent taking write");
                interestOps.remove(OP_WRITE);
                LOGGER.debug("handleEvent submitting new runnable for write");
                executorService.submit(relpWrite);
                LOGGER.debug("handleEvent exiting write");
            }
        }
        else {
            // encrypted connections, reads may need writes too and vice versa
            if (selectionKey.isReadable()) {
                interestOps.remove(OP_READ);
                // socket write may be pending a tls read
                if (relpWrite.needRead.compareAndSet(true, false)) {
                    executorService.submit(relpWrite);
                }

                // read anyway
                executorService.submit(relpRead);
            }

            if (selectionKey.isWritable()) {
                if (relpRead.needWrite.compareAndSet(true, false)) {
                    // socket read may be pending a tls write
                    executorService.submit(relpRead);
                }

                // write anyway
                executorService.submit(relpWrite);
            }
        }
    }

    InterestOps interestOps() {
        return this.interestOps;
    }
}
