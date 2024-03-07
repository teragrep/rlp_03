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

package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class SocketPoll implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final ExecutorService executorService;

    private final SocketFactory socketFactory;
    private final Selector selector;

    private final ServerSocketChannel serverSocketChannel;

    private final Supplier<FrameDelegate> frameDelegateSupplier;

    private final ConnectionContextStub connectionContextStub;


    public SocketPoll(
            ExecutorService executorService,
            SocketFactory socketFactory,
            Selector selector,
            ServerSocketChannel serverSocketChannel,
            Supplier<FrameDelegate> frameDelegateSupplier
    ) {
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
        this.frameDelegateSupplier = frameDelegateSupplier;
        this.connectionContextStub = new ConnectionContextStub();
    }

    public void poll() throws IOException {
        int readyKeys = selector.select();

        LOGGER.debug("readyKeys <{}>",readyKeys);

        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        LOGGER.debug("selectionKeys <{}> ", selectionKeys);
        for (SelectionKey selectionKey : selectionKeys) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "selectionKey <{}>: " +
                                "isValid <{}>, " +
                                "isConnectable <{}>, " +
                                "isAcceptable <{}>, " +
                                "isReadable <{}>, " +
                                "isWritable <{}>",
                        selectionKey,
                        selectionKey.isValid(),
                        selectionKey.isConnectable(),
                        selectionKey.isAcceptable(),
                        selectionKey.isReadable(),
                        selectionKey.isWritable()
                );
            }
            try {
                if (selectionKey.isAcceptable()) {
                    processAccept(serverSocketChannel, selectionKey);
                } else {
                    // submit readTask/writeTask based on clientContext states
                    ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                    try {
                        connectionContext.handleEvent(selectionKey);
                    }
                    catch (CancelledKeyException cke) {
                        LOGGER.warn("SocketPoll.poll CancelledKeyException caught: {}", cke.getMessage());
                        connectionContext.close();
                    }
                }
            } catch (CancelledKeyException cke) { // TODO is this proper to catch here?
                LOGGER.warn("SocketPoll.poll CancelledKeyException caught: {}", cke.getMessage());
                selectionKey.channel().close();
            }

        }
        selectionKeys.clear();
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();
        selector.close();
    }

    private void processAccept(ServerSocketChannel serverSocketChannel, SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel clientSocketChannel = serverSocketChannel.accept();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ServerSocket <{}> accepting ClientSocket <{}> ", serverSocketChannel.getLocalAddress(), clientSocketChannel.getRemoteAddress());
            }

            // tls/plain wrapper
            Socket socket = socketFactory.create(clientSocketChannel);



            // non-blocking
            clientSocketChannel.configureBlocking(false);

            SelectionKey clientSelectionKey = clientSocketChannel.register(
                    selector,
                    0, // interestOps: none at this point
                    connectionContextStub
            );

            InterestOps interestOps = new InterestOpsImpl(clientSelectionKey);

            ConnectionContext connectionContext = new ConnectionContextImpl(
                    executorService,
                    socket,
                    interestOps,
                    frameDelegateSupplier.get()
            );

            clientSelectionKey.attach(connectionContext);

            // proper attachment attached, now it is safe to use
            clientSelectionKey.interestOps(SelectionKey.OP_READ);
        }
    }
}
