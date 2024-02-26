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

import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_03.config.Config;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.events.Characters;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SendMessageTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendMessageTest.class);

    private final String hostname = "localhost";
    private Server server;
    private static int port = 1236;

    private final List<byte[]> messageList = new LinkedList<>();

    @BeforeAll
    public void init() {
        port = getPort();
        Config config = new Config(port, 1);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor((frame) -> messageList.add(frame.relpFrame().payload().toBytes())));
        Assertions.assertAll(() -> {
            server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();
        });
    }

    @AfterAll
    public void cleanup() {
        server.stop();
    }

    private synchronized int getPort() {
        return ++port;
    }

    @Test
    public void testSendMessage() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        String msg = "<14>1 2020-05-15T13:24:03.603Z CFE-16 capsulated - - [CFE-16-metadata@48577 authentication_token=\"AUTH_TOKEN_11111\" channel=\"CHANNEL_11111\" time_source=\"generated\"][CFE-16-origin@48577] \"Hello, world!\"\n";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch = new RelpBatch();
        long reqId = batch.insert(data);
        Assertions.assertAll(() -> relpSession.commit(batch));
        // verify successful transaction
        Assertions.assertTrue(batch.verifyTransaction(reqId));
        Assertions.assertAll(relpSession::disconnect);

        // message must equal to what was send
        Assertions.assertEquals(msg, new String(messageList.get(0)));

        // clear received list
        messageList.clear();
    }

    @Test
    public void testSendSmallMessage() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        String msg = "<167>Mar  1 01:00:00 1um:\n";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch = new RelpBatch();
        long reqId = batch.insert(data);
        Assertions.assertAll(() -> relpSession.commit(batch));
        // verify successful transaction
        Assertions.assertTrue(batch.verifyTransaction(reqId));
        Assertions.assertAll(relpSession::disconnect);

        // message must equal to what was send
        Assertions.assertEquals(msg, new String(messageList.get(0)));

        // clear received list
        messageList.clear();
    }


    @Test
    public void testOpenAndCloseSession() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        Assertions.assertAll(relpSession::disconnect);
    }

    @Test
    public void testSessionCloseTwice() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        Assertions.assertAll(relpSession::disconnect);
        Assertions.assertThrows(IllegalStateException.class, relpSession::disconnect);

    }

    @Test
    public void clientTestOpenSendClose() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        String msg = "clientTestOpenSendClose";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch = new RelpBatch();
        batch.insert(data);
        Assertions.assertAll(() -> relpSession.commit(batch));
        Assertions.assertTrue(batch.verifyTransactionAll());
        Assertions.assertAll(relpSession::disconnect);

        // message must equal to what was send
        Assertions.assertEquals(msg, new String(messageList.get(0)));

        // clear received list
        messageList.clear();
    }

    @Test
    public void clientTestSendTwo() {
        RelpConnection relpSession = new RelpConnection();
        relpSession.setConnectionTimeout(5000);
        relpSession.setReadTimeout(5000);
        relpSession.setWriteTimeout(5000);
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace( "test> Connected");
            Assertions.assertAll(() -> Thread.sleep(1000));
        }
        String msg1 = "clientTestOpenSendClose 1";
        byte[] data1 = msg1.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch1 = new RelpBatch();
        batch1.insert(data1);
        Assertions.assertAll(() -> relpSession.commit(batch1));
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace( "test> Committed");
            Assertions.assertAll(() -> Thread.sleep(1000));
        }
        Assertions.assertTrue(batch1.verifyTransactionAll());

        String msg2 = "clientTestOpenSendClose 2";
        byte[] data2 = msg2.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch2 = new RelpBatch();
        batch2.insert(data2);
        Assertions.assertAll(() -> relpSession.commit(batch2));
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace( "test> Committed second");
            Assertions.assertAll(() -> Thread.sleep(1000));
        }
        Assertions.assertTrue(batch1.verifyTransactionAll());
        Assertions.assertAll(relpSession::disconnect);
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace( "test> Disconnected");
            Assertions.assertAll(() -> Thread.sleep(1000));
        }

        // messages must equal to what was send
        Assertions.assertEquals(msg1, new String(messageList.get(0)));
        Assertions.assertEquals(msg2, new String(messageList.get(1)));

        // clear received list
        messageList.clear();
    }

    @Test
    public void testSendBatch() {
        RelpConnection relpSession = new RelpConnection();
        Assertions.assertAll(() -> relpSession.connect(hostname, port));
        String msg = "Hello, world!";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        int n = 50;
        RelpBatch batch = new RelpBatch();
        for (int i = 0; i < n; i++) {
            batch.insert(data);
        }
        Assertions.assertAll(() -> relpSession.commit(batch));
        Assertions.assertTrue(batch.verifyTransactionAll());
        Assertions.assertAll(relpSession::disconnect);

        for (int i = 0; i < n; i++) {
            Assertions.assertEquals(msg, new String(messageList.get(i)));
        }

        // clear afterwards
        messageList.clear();
    }

}
