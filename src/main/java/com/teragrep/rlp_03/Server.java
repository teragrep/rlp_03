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

import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A class that starts the server connection to the client. Fires up a new thread
 * for the Socket Processor.
 */
public class Server implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    public final ThreadPoolExecutor executorService;

    private final ServerSocketChannel serverSocketChannel;
    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final SocketFactory socketFactory;
    private final Selector selector;

    private final AtomicBoolean stop;

    public final Status startup;


    public Server(
            ThreadPoolExecutor threadPoolExecutor,
            Supplier<FrameProcessor> frameProcessorSupplier,
            ServerSocketChannel serverSocketChannel,
            SocketFactory socketFactory,
            Selector selector
    ) {

        this.executorService = threadPoolExecutor;
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.serverSocketChannel = serverSocketChannel;
        this.socketFactory = socketFactory;
        this.selector = selector;

        this.stop = new AtomicBoolean();
        this.startup = new Status();
    }

    public void stop() {
        LOGGER.debug("stopping");

        if (!startup.isComplete()) {
            throw new IllegalStateException("can not stop non-started instance");
        }

        stop.set(true);
        selector.wakeup();
    }

    @Override
    public void run() {
        try (SocketPoll socketPoll = new SocketPoll(executorService,socketFactory, selector, serverSocketChannel, frameProcessorSupplier)) {
            startup.complete(); // indicate successful startup
            LOGGER.debug("Started");
            while (!stop.get()) {
                socketPoll.poll();
            }
            // shutdown executorService when outside of poll() loop
            executorService.shutdown();
        }
        catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        LOGGER.debug("Stopped");
    }
}