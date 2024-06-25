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

import com.teragrep.net_01.channel.context.ConnectContextFactory;
import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.channel.socket.SocketFactory;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.server.ServerFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClientTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTest.class);
    private EventLoop eventLoop;
    private Thread eventLoopThread;
    private final int port = 23601;
    private ExecutorService executorService;

    @BeforeAll
    public void init() {
        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        Assertions.assertAll(() -> eventLoop = eventLoopFactory.create());

        eventLoopThread = new Thread(eventLoop);
        eventLoopThread.start();

        executorService = Executors.newSingleThreadExecutor();
        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new PlainFactory(),
                new FrameDelegationClockFactory(() -> new DefaultFrameDelegate((frame) -> LOGGER.debug("server got <[{}]>", frame.relpFrame())))
        );

        Assertions.assertAll(() -> serverFactory.create(port));
    }

    @AfterAll
    public void cleanup() {
        eventLoop.stop();
        executorService.shutdown();
        Assertions.assertAll(eventLoopThread::join);
    }

    @Test
    public void testClient() throws IOException {

        // client takes resources from this pool
        ExecutorService executorService = Executors.newCachedThreadPool();

        // client connection type
        SocketFactory socketFactory = new PlainFactory();

        ConnectContextFactory connectContextFactory = new ConnectContextFactory(executorService, socketFactory);
        RelpClientFactory relpClientFactory = new RelpClientFactory(connectContextFactory, eventLoop);

        RelpFrameFactory relpFrameFactory = new RelpFrameFactory();

        try (
                RelpClient relpClient = relpClientFactory.open(new InetSocketAddress("localhost", port)).get(1, TimeUnit.SECONDS)
        ) {

            // send open
            CompletableFuture<RelpFrame> open = relpClient
                    .transmit(relpFrameFactory.create("open", "a hallo yo client"));

            // send syslog
            CompletableFuture<RelpFrame> syslog = relpClient
                    .transmit(relpFrameFactory.create("syslog", "yonnes payload"));

            // send close
            CompletableFuture<RelpFrame> close = relpClient.transmit(relpFrameFactory.create("close", ""));

            // test open response
            try (RelpFrame openResponse = open.get()) {
                LOGGER.debug("openResponse <[{}]>", openResponse);
                Assertions.assertEquals("rsp", openResponse.command().toString());
                Pattern pattern = Pattern
                        .compile(
                                "200 OK\\nrelp_version=0\\nrelp_software=RLP-01,[0-9]+.[0-9]+.[0-9]+(-[a-zA-Z0-9]+)?,https://teragrep.com\\ncommands=syslog\\n"
                        );
                Matcher matcher = pattern.matcher(openResponse.payload().toString());
                Assertions.assertTrue(matcher.find());
            } // close the openResponse frame, free resources

            // test syslog response
            try (RelpFrame syslogResponse = syslog.get()) {
                Assertions.assertEquals("rsp", syslogResponse.command().toString());
                LOGGER.debug("syslogResponse <[{}]>", syslogResponse);
                Assertions.assertEquals("200 OK", syslogResponse.payload().toString());
            } // close the syslogResponse frame, free resources

            // test close response
            try (RelpFrame closeResponse = close.get()) {
                Assertions.assertEquals("rsp", closeResponse.command().toString());
                LOGGER.debug("closeResponse <[{}]>", closeResponse);
                Assertions.assertEquals("", closeResponse.payload().toString());
            } // close the closeResponse frame, free resources
        }
        catch (InterruptedException | ExecutionException | TimeoutException exception) {
            throw new RuntimeException(exception);
        }
    }
}
