/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021,2024  Suomen Kanuuna Oy
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

import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.Server;
import com.teragrep.rlp_03.ServerFactory;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.context.frame.RelpFrame;
import com.teragrep.rlp_03.delegate.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
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
        Config config = new Config(listenPort, threads);

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
         * DefaultFrameDelegate accepts Consumer<FrameContext> for processing syslog frames
         */
        DefaultFrameDelegate frameDelegate = new DefaultFrameDelegate(syslogConsumer);

        /*
         * Same instance of the frameDelegate is shared with every connection
         */
        Supplier<FrameDelegate> frameDelegateSupplier = new Supplier<FrameDelegate>() {
            @Override
            public FrameDelegate get() {
                System.out.println("Providing frameDelegate for a connection");
                return frameDelegate;
            }
        };

        /*
         * ServerFactory is used to create server instances
         */
        ServerFactory serverFactory = new ServerFactory(config, frameDelegateSupplier);

        Server server;
        try {
            server = serverFactory.create();
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
         * Send Hello, World! via rlp_01
         */
        new ExampleRelpClient(listenPort).send("Hello, World!");

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * ExampleRelpClient using rlp_01 for demonstration
     */
    private class ExampleRelpClient {
        private final int port;
        ExampleRelpClient(int port) {
            this.port = port;
        }

        public void send(String record) {
            RelpConnection relpConnection = new RelpConnection();
            try {
                relpConnection.connect("localhost", port);
            }
            catch (IOException | TimeoutException exception) {
                throw new RuntimeException(exception);
            }

            RelpBatch relpBatch = new RelpBatch();
            relpBatch.insert(record.getBytes(StandardCharsets.UTF_8));

            while (!relpBatch.verifyTransactionAll()) {
                relpBatch.retryAllFailed();
                try {
                    relpConnection.commit(relpBatch);
                }
                catch (IOException | TimeoutException exception) {
                    throw new RuntimeException(exception);
                }
            }
            try {
                relpConnection.disconnect();
            }
            catch (IOException | TimeoutException exception) {
                throw new RuntimeException(exception);
            }
            finally {
                relpConnection.tearDown();
            }
        }
    }
}
