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

import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.config.TLSConfig;
import com.teragrep.rlp_03.context.ConnectionContextStub;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.context.channel.TLSFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class ServerFactory {

    final Config config;
    final TLSConfig tlsConfig;
    final Supplier<FrameDelegate> frameDelegateSupplier;

    final ThreadPoolExecutor executorService;
    final ConnectionContextStub connectionContextStub;
    final InetSocketAddress listenSocketAddress;


    public ServerFactory(Config config, Supplier<FrameDelegate> frameDelegateSupplier) {
        this(config, new TLSConfig(), frameDelegateSupplier);
    }


    public ServerFactory(
            Config config,
            TLSConfig tlsConfig,
            Supplier<FrameDelegate> frameDelegateSupplier
    ) {

        this.config = config;
        this.tlsConfig = tlsConfig;
        this.frameDelegateSupplier = frameDelegateSupplier;

        this.executorService = new ThreadPoolExecutor(config.numberOfThreads, config.numberOfThreads, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.connectionContextStub = new ConnectionContextStub();
        this.listenSocketAddress = new InetSocketAddress(config.port);
    }

    public Server create() throws IOException {
        config.validate();

        SocketFactory socketFactory;
        if (tlsConfig.useTls) {
            socketFactory = new TLSFactory(tlsConfig.getSslContext(), tlsConfig.getSslEngineFunction());
        }
        else {
            socketFactory = new PlainFactory();
        }

        Selector selector = Selector.open();
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            try {
                serverSocketChannel.socket().setReuseAddress(true);
                serverSocketChannel.bind(listenSocketAddress);
                serverSocketChannel.configureBlocking(false);
                serverSocketChannel.register(selector, OP_ACCEPT);
                return new Server(executorService, frameDelegateSupplier, serverSocketChannel, socketFactory, selector);
            }
            catch (IOException ioException) {
                serverSocketChannel.close();
                throw ioException;
            }

        }
        catch (IOException ioException) {
            selector.close();
            throw ioException;
        }
    }
}
