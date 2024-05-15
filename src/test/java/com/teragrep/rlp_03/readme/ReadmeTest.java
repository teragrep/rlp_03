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
package com.teragrep.rlp_03.readme;

import com.teragrep.rlp_03.eventloop.EventLoop;
import com.teragrep.rlp_03.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_03.server.ServerFactory;
import com.teragrep.rlp_03.channel.socket.PlainFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * For use cases in the README.adoc
 */
public class ReadmeTest {

    @Test
    public void testServerSetup() {
        int listenPort = 10601;
        int threads = 1; // processing threads shared across the connections
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        /*
         * System.out.println is used to print the frame payload
         */
        Consumer<FrameContext> syslogConsumer = new Consumer<FrameContext>() {

            // NOTE: synchronized because frameDelegateSupplier returns this instance for all the parallel connections
            @Override
            public synchronized void accept(FrameContext frameContext) {
                System.out.println(frameContext.relpFrame().payload().toString());
            }
        };

        /*
         * New instance of the frameDelegate is provided for every connection
         */
        Supplier<FrameDelegate> frameDelegateSupplier = () -> {
            System.out.println("Providing frameDelegate for a connection");
            return new DefaultFrameDelegate(syslogConsumer);
        };

        /*
         * EventLoop is used to notice any events from the connections
         */
        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        EventLoop eventLoop;
        try {
            eventLoop = eventLoopFactory.create();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        Thread eventLoopThread = new Thread(eventLoop);
        /*
         * eventLoopThread must run, otherwise nothing will be processed
         */
        eventLoopThread.start();

        /*
         * ServerFactory is used to create server instances
         */
        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new PlainFactory(),
                frameDelegateSupplier
        );

        try {
            serverFactory.create(listenPort);
            System.out.println("server started at port <" + listenPort + ">");
        }
        catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        /*
         * Send Hello, World! via rlp_01
         */
        new ExampleRelpClient(listenPort).send("Hello, World!");
        new ExampleRelpClient(listenPort).send("Hello, World again!");

        /*
         * Stop eventLoop
         */
        eventLoop.stop();

        /*
         * Wait for stop to complete
         */
        try {
            eventLoopThread.join();
        }
        catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }
        System.out.println("server stopped at port <" + listenPort + ">");

        executorService.shutdown();
    }
}
