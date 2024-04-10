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
package com.teragrep.rlp_03.channel.context;

import com.teragrep.rlp_03.channel.socket.SocketFactory;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Factory for creating {@link ConnectContext} type {@link Context}s.
 */
public class ConnectContextFactory {

    private final ExecutorService executorService;
    private final SocketFactory socketFactory;

    /**
     *
     * @param executorService to handle connection's events with
     * @param socketFactory that produces the desired type {@link com.teragrep.rlp_03.channel.socket.Socket} for the connection
     */
    public ConnectContextFactory(ExecutorService executorService, SocketFactory socketFactory) {
        this.executorService = executorService;
        this.socketFactory = socketFactory;
    }

    /**
     *
     * @param inetSocketAddress address to initiate connection to.
     * @param frameDelegate for processing received data with.
     * @param establishedContextConsumer for handling the callback once connection is established.
     * @return ConnectContext to be registered with {@code {@link com.teragrep.rlp_03.EventLoop}.register()}.
     * @throws IOException if underlying socketChannel is unable to initiate the connection.
     */
    public ConnectContext create(
            InetSocketAddress inetSocketAddress,
            FrameDelegate frameDelegate,
            Consumer<EstablishedContext> establishedContextConsumer
    ) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        try {
            socketChannel.socket().setKeepAlive(true);
            socketChannel.configureBlocking(false);
            socketChannel.connect(inetSocketAddress);
        }
        catch (IOException ioException) {
            socketChannel.close();
            throw ioException;
        }

        return new ConnectContext(
                socketChannel,
                executorService,
                socketFactory,
                frameDelegate,
                establishedContextConsumer
        );
    }
}
