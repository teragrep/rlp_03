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
package com.teragrep.rlp_03.frame.delegate;

import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_01.RelpFrameRX;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DefaultFrameDelegateExceptionTest {

    private final String hostname = "localhost";
    private int port;
    private EventLoop eventLoop;
    private Thread eventLoopThread;
    private ExecutorService executorService;

    @BeforeAll
    public void init() {
        port = 45601;

        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        Assertions.assertAll(() -> eventLoop = eventLoopFactory.create());

        eventLoopThread = new Thread(eventLoop);
        eventLoopThread.start();

        executorService = Executors.newSingleThreadExecutor();
        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new PlainFactory(),
                new FrameDelegationClockFactory(() -> new DefaultFrameDelegate((frame) -> {
                    throw new UnsupportedOperationException("this exception is expected");
                }))
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
    public void testException() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        String msg = "fail this";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch = new RelpBatch();
        long reqId = batch.insert(data);
        Assertions.assertAll(() -> relpSession.commit(batch));

        RelpFrameRX response = batch.getResponse(reqId);
        Assertions.assertEquals(2, response.getTransactionNumber());
        Assertions.assertEquals("rsp", response.getCommand());
        Assertions.assertEquals(500, response.getResponseCode());
        Assertions
                .assertEquals(
                        "500 EXCEPTION WHILE PROCESSING SYSLOG PAYLOAD",
                        new String(response.getData(), StandardCharsets.UTF_8)
                );

        Assertions.assertAll(relpSession::disconnect);
    }
}
