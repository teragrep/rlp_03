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

import com.teragrep.rlp_03.FrameDelegate;
import com.teragrep.rlp_03.context.buffer.BufferLeasePool;
import com.teragrep.rlp_03.context.channel.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * A per connection object that handles reading and writing messages from and to
 * the SocketChannel.
 */
public class ConnectionContextImpl implements ConnectionContext { // TODO make package-protected
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionContextImpl.class);

    private final ExecutorService executorService;
    private final Socket socket;
    private final InterestOps interestOps;
    private final FrameDelegate frameDelegate;

    private final BufferLeasePool bufferLeasePool;
    private final RelpRead relpRead;
    private final RelpWriteImpl relpWrite;



    public ConnectionContextImpl(ExecutorService executorService, Socket socket, InterestOps interestOps, FrameDelegate frameDelegate) {
        this.interestOps = interestOps;
        this.executorService = executorService;
        this.socket = socket;
        this.frameDelegate = frameDelegate;

        this.bufferLeasePool = new BufferLeasePool();
        this.relpRead = new RelpReadImpl(this, this.frameDelegate, this.bufferLeasePool);
        this.relpWrite = new RelpWriteImpl(this);

    }

    @Override
    public void close() {
        try {
            interestOps.removeAll();
        }
        catch (CancelledKeyException cke) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("CancelledKeyException <{}> in close", cke.getMessage());
            }
        }

        try {
            frameDelegate.close();
        }
        catch (Exception exception) {
            LOGGER.warn("FrameDelegate close threw exception <{}>", exception.getMessage());
        }

        try {
            socket.close();
        }
        catch (IOException ioe) {
            LOGGER.warn("IOException <{}> in close", ioe.getMessage());
        }

        bufferLeasePool.close();
    }


    @Override
    public void handleEvent(SelectionKey selectionKey) {
        if (!socket.getTransportInfo().getEncryptionInfo().isEncrypted()) {
            // plain connection, reads are reads, writes are writes

            if (selectionKey.isReadable()) { // perhaps track read/write needs here per direction too
                LOGGER.debug("handleEvent taking read");
                try {
                    interestOps.remove(OP_READ);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("CancelledKeyException <{}> in handleEvent. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), socket.getTransportInfo().getPeerAddress(), socket.getTransportInfo().getPeerPort());
                    close();
                    return;
                }
                LOGGER.debug("handleEvent submitting new runnable for read");
                try {
                    executorService.submit(relpRead);
                }
                catch (RejectedExecutionException ree) {
                    LOGGER.error("executorService.submit threw <{}> for read", ree.getMessage());
                    throw ree;
                }
                LOGGER.debug("handleEvent exiting read");
            }

            if (selectionKey.isWritable()) {
                LOGGER.debug("handleEvent taking write");
                try {
                    interestOps.remove(OP_WRITE);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("CancelledKeyException <{}> in handleEvent. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), socket.getTransportInfo().getPeerAddress(), socket.getTransportInfo().getPeerPort());
                    close();
                    return;
                }
                LOGGER.debug("handleEvent submitting new runnable for write");
                try {
                    executorService.submit(relpWrite);
                    LOGGER.info("submitted write!");
                }
                catch (RejectedExecutionException ree) {
                    LOGGER.error("executorService.submit threw <{}> for write", ree.getMessage());
                    throw ree;
                }
                LOGGER.debug("handleEvent exiting write");
            }
        }
        else {
            // encrypted connections, reads may need writes too and vice versa
            if (selectionKey.isReadable()) {
                try {
                    interestOps.remove(OP_READ);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("CancelledKeyException <{}> in handleEvent. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), socket.getTransportInfo().getPeerAddress(), socket.getTransportInfo().getPeerPort());
                    close();
                    return;
                }
                // socket write may be pending a tls read
                if (relpWrite.needRead().compareAndSet(true, false)) {
                    executorService.submit(relpWrite);
                }

                // read anyway
                executorService.submit(relpRead);
            }

            if (selectionKey.isWritable()) {
                try {
                    interestOps.remove(OP_WRITE);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("CancelledKeyException <{}> in handleEvent. Closing connection for PeerAddress <{}> PeerPort <{}>", cke.getMessage(), socket.getTransportInfo().getPeerAddress(), socket.getTransportInfo().getPeerPort());
                    close();
                    return;
                }
                if (relpRead.needWrite().compareAndSet(true, false)) {
                    // socket read may be pending a tls write
                    executorService.submit(relpRead);
                }

                // write anyway
                executorService.submit(relpWrite);
            }
        }
    }

    @Override
    public InterestOps interestOps() {
        return interestOps;
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public RelpWrite relpWrite() {
        return relpWrite;
    }
}
