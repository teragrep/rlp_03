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
package com.teragrep.rlp_03.client;

import com.teragrep.rlp_03.EventLoopFactory;
import com.teragrep.rlp_03.channel.context.ConnectContextFactory;
import com.teragrep.rlp_03.channel.socket.PlainFactory;
import com.teragrep.rlp_03.channel.socket.SocketFactory;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_03.server.Server;
import com.teragrep.rlp_03.server.ServerFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StuckClientCloseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTest.class);
    private Server server;
    private Thread serverThread;
    private final int port = 23602;
    private ExecutorService executorService;

    @BeforeAll
    public void init() {
        executorService = Executors.newSingleThreadExecutor();

        FrameDelegate stuckFrameDelegate = new FrameDelegate() {

            @Override
            public boolean accept(FrameContext frameContext) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            @Override
            public void close() {
                // no-op
            }

            @Override
            public boolean isStub() {
                return false;
            }
        };

        ServerFactory serverFactory = new ServerFactory(executorService, new PlainFactory(), () -> stuckFrameDelegate);

        Assertions.assertAll(() -> {
            server = serverFactory.create(port);

            serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();
        });
    }

    @AfterAll
    public void cleanup() {
        server.stop();
        executorService.shutdown();
        Assertions.assertAll(() -> serverThread.join());
    }

    @Test
    public void testStuckClient() throws IOException {
        // runs the client eventLoop (one may share eventLoop with a server too, or other clients)
        RunnableEventLoop runnableEventLoop = new RunnableEventLoop(new EventLoopFactory().create());
        Thread eventLoopThread = new Thread(runnableEventLoop);
        eventLoopThread.start();

        // client takes resources from this pool
        ExecutorService executorService = Executors.newCachedThreadPool();

        // client connection type
        SocketFactory socketFactory = new PlainFactory();

        ConnectContextFactory connectContextFactory = new ConnectContextFactory(executorService, socketFactory);
        ClientFactory clientFactory = new ClientFactory(connectContextFactory, runnableEventLoop.eventLoop());

        try (Client client = clientFactory.open(new InetSocketAddress("localhost", port), 1, TimeUnit.SECONDS)) {

            // send open
            CompletableFuture<RelpFrame> open = client
                    .transmit("open", "open be stuck".getBytes(StandardCharsets.UTF_8));

            // send syslog
            CompletableFuture<RelpFrame> syslog = client
                    .transmit("syslog", "this syslog is not processed either ".getBytes(StandardCharsets.UTF_8));

            // send close
            CompletableFuture<RelpFrame> close = client
                    .transmit("close be stuck too", "".getBytes(StandardCharsets.UTF_8));

            // closing the client, now futures should complete exceptionally
            client.close();

            AtomicLong completedTransactions = new AtomicLong();
            // test open response
            Assertions.assertTrue(open.isCompletedExceptionally());
            open.whenComplete((relpFrame, throwable) -> {
                Assertions
                        .assertEquals(
                                "TransactionService closed before transaction was completed.", throwable.getMessage()
                        );
                Assertions.assertInstanceOf(TransactionServiceClosedException.class, throwable);
                completedTransactions.getAndIncrement();
            });

            // test syslog response
            Assertions.assertTrue(syslog.isCompletedExceptionally());
            syslog.whenComplete((relpFrame, throwable) -> {
                Assertions
                        .assertEquals(
                                "TransactionService closed before transaction was completed.", throwable.getMessage()
                        );
                Assertions.assertInstanceOf(TransactionServiceClosedException.class, throwable);
                completedTransactions.getAndIncrement();
            });

            // test close response
            Assertions.assertTrue(close.isCompletedExceptionally());
            close.whenComplete((relpFrame, throwable) -> {
                Assertions
                        .assertEquals(
                                "TransactionService closed before transaction was completed.", throwable.getMessage()
                        );
                Assertions.assertInstanceOf(TransactionServiceClosedException.class, throwable);
                completedTransactions.getAndIncrement();
            });

            Assertions.assertEquals(3, completedTransactions.get());

            // close the client eventLoop
            runnableEventLoop.close();
            eventLoopThread.join();
        }
        catch (InterruptedException | ExecutionException | IOException | TimeoutException exception) {
            throw new RuntimeException(exception);
        }
    }
}
