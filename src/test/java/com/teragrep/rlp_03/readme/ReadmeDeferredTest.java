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

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.RelpFrameFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.delegate.event.RelpEvent;
import com.teragrep.rlp_03.frame.delegate.event.RelpEventClose;
import com.teragrep.rlp_03.frame.delegate.event.RelpEventOpen;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * For use cases in the README.adoc
 */
public class ReadmeDeferredTest {

    @Test
    public void testDeferredFrameDelegate() {
        int listenPort = 10602;
        int threads = 1; // processing threads shared across the connections
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        /*
         * DefaultFrameDelegate accepts Map<String, RelpEvent> for processing of the commands
         */

        Map<String, RelpEvent> relpCommandConsumerMap = new HashMap<>();
        /*
         * Add default commands, open and close, they are mandatory
         */
        relpCommandConsumerMap.put(RelpCommand.OPEN, new RelpEventOpen());
        relpCommandConsumerMap.put(RelpCommand.CLOSE, new RelpEventClose());

        /*
         * Queue for deferring the processing of the frames
         */
        BlockingQueue<FrameContext> frameContexts = new ArrayBlockingQueue<>(1024);
        RelpEvent syslogRelpEvent = new RelpEvent() {

            @Override
            public void accept(FrameContext frameContext) {
                frameContexts.add(frameContext);
            }

            @Override
            public void close() {
                frameContexts.clear();
            }
        };

        relpCommandConsumerMap.put(RelpCommand.SYSLOG, syslogRelpEvent);

        /*
         * New instance of the frameDelegate is provided for every connection
         */
        Supplier<FrameDelegate> frameDelegateSupplier = () -> {
            System.out.println("Providing frameDelegate for a connection");
            return new DefaultFrameDelegate(relpCommandConsumerMap);
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
                new FrameDelegationClockFactory(frameDelegateSupplier)
        );

        try {
            serverFactory.create(listenPort);
            System.out.println("server started at port <" + listenPort + ">");
        }
        catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        /*
         * Start deferred processing, otherwise our client will wait forever for a response
         */
        DeferredSyslog deferredSyslog = new DeferredSyslog(frameContexts);
        Thread deferredProcessingThread = new Thread(deferredSyslog);
        deferredProcessingThread.start();

        /*
         * Send Hello, World! via rlp_01
         */
        new ExampleRelpClient(listenPort).send("Hello, Deferred World!");
        new ExampleRelpClient(listenPort).send("Hello, Deferred World again!");

        /*
         * Stop eventLoop
         */
        eventLoop.stop();

        /*
         * Wait for stop to complete
         */
        try {
            eventLoopThread.join();
            System.out.println("eventLoop stopped");
        }
        catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }

        /*
         * Stop the deferred processing thread
         */
        deferredSyslog.run.set(false);
        try {
            deferredProcessingThread.join();
        }
        catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }
        executorService.shutdown();
    }

    private class DeferredSyslog implements Runnable {

        private final BlockingQueue<FrameContext> frameContexts;

        public final AtomicBoolean run;

        DeferredSyslog(BlockingQueue<FrameContext> frameContexts) {
            this.frameContexts = frameContexts;

            this.run = new AtomicBoolean(true);
        }

        @Override
        public void run() {
            while (run.get()) {
                try {
                    // this will read at least one
                    FrameContext frameContext = frameContexts.poll(1, TimeUnit.SECONDS);

                    if (frameContext == null) {
                        // no frame yet
                        continue;
                    }

                    // try-with-resources so frame is closed and freed,
                    try (RelpFrame relpFrame = frameContext.relpFrame()) {
                        System.out
                                .println(
                                        this.getClass().getSimpleName() + " payload <[" + relpFrame.payload().toString()
                                                + "]>"
                                );

                        RelpFrameFactory relpFrameFactory = new RelpFrameFactory();
                        // create a response for the frame
                        RelpFrame responseFrame = relpFrameFactory.create(relpFrame.txn().toBytes(), "rsp", "200 OK");

                        // WARNING: failing to respond causes transaction aware clients to wait
                        frameContext.establishedContext().egress().accept(responseFrame.toWriteable());
                    }
                }
                catch (Exception interruptedException) {
                    // ignored
                }
            }

        }
    }
}
