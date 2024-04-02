/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
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
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ConnectContext implements Context {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectContext.class);

    private final SocketChannel socketChannel;
    private final ExecutorService executorService;
    private final SocketFactory socketFactory;
    private final FrameDelegate frameDelegate;

    private final Consumer<ConnectionContext> connectionContextConsumer;

    public ConnectContext(
            SocketChannel socketChannel,
            ExecutorService executorService,
            SocketFactory socketFactory,
            FrameDelegate frameDelegate,
            Consumer<ConnectionContext> connectionContextConsumer
    ) {
        this.socketChannel = socketChannel;
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.connectionContextConsumer = connectionContextConsumer;
        this.frameDelegate = frameDelegate;
    }

    public void register(EventLoop eventLoop) throws ClosedChannelException {
        socketChannel.register(eventLoop.selector(), SelectionKey.OP_CONNECT, this);
    }

    @Override
    public void handleEvent(SelectionKey selectionKey) {
        if (selectionKey.isConnectable()) {
            try {
                if (!socketChannel.finishConnect()) {
                    // not yet complete
                    return;
                }
            }
            catch (IOException ioException) {
                LOGGER.warn("socketChannel <{}> finishConnect threw, closing", socketChannel, ioException);
                close();
                return;
            }

            // No need to be longer interested in connect.
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_CONNECT);

            InterestOps interestOps = new InterestOpsImpl(selectionKey);

            ConnectionContext connectionContext = new ConnectionContextImpl(
                    executorService,
                    socketFactory.create(socketChannel),
                    interestOps,
                    frameDelegate
            );
            // change attachment to established -> ConnectionContext
            selectionKey.attach(connectionContext);

            interestOps.add(SelectionKey.OP_READ);

            LOGGER.info("ready");
            connectionContextConsumer.accept(connectionContext);
        }
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        }
        catch (IOException ioException) {
            LOGGER.warn("socketChannel <{}> close threw", socketChannel, ioException);
        }
    }
}
