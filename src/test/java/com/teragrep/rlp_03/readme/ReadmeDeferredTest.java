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
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_03.server.Server;
import com.teragrep.rlp_03.server.ServerFactory;
import com.teragrep.rlp_03.channel.socket.PlainFactory;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.delegate.event.RelpEvent;
import com.teragrep.rlp_03.frame.delegate.event.RelpEventClose;
import com.teragrep.rlp_03.frame.delegate.event.RelpEventOpen;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
         * Register the commands to the DefaultFrameDelegate
         */
        FrameDelegate frameDelegate = new DefaultFrameDelegate(relpCommandConsumerMap);

        /*
         * Same instance of the frameDelegate is shared with every connection, BlockingQueues are thread-safe
         */
        Supplier<FrameDelegate> frameDelegateSupplier = () -> {
            System.out.println("Providing frameDelegate for a connection");
            return frameDelegate;
        };

        /*
         * ServerFactory is used to create server instances
         */
        ServerFactory serverFactory = new ServerFactory(executorService, new PlainFactory(), frameDelegateSupplier);

        Server server;
        try {
            server = serverFactory.create(listenPort);
        }
        catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        /*
         * One may use server.run(); or create the server into a new thread
         */
        Thread serverThread = new Thread(server);

        /*
         * Run the server
         */
        serverThread.start();

        /*
         * Wait for startup, server is available for connections once it finished setup
         */
        try {
            server.startup.waitForCompletion();
            System.out.println("server started at port <" + listenPort + ">");
        }
        catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
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

        /*
         * Stop server
         */
        server.stop();

        /*
         * Wait for stop to complete
         */
        try {
            serverThread.join();
        }
        catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }
        System.out.println("server stopped at port <" + listenPort + ">");

        /*
         * Close the frameDelegate
         */
        try {
            frameDelegate.close();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
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

                        // create a response for the frame
                        RelpFrameTX frameResponse = new RelpFrameTX("rsp", "200 OK".getBytes(StandardCharsets.UTF_8));

                        // set transaction number
                        frameResponse.setTransactionNumber(relpFrame.txn().toInt());

                        // WARNING: failing to respond causes transaction aware clients to wait
                        frameContext.establishedContext().relpWrite().accept(Collections.singletonList(frameResponse));
                    }
                }
                catch (Exception interruptedException) {
                    // ignored
                }
            }

        }
    }
}
