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
import com.teragrep.rlp_03.delegate.SyslogFrameProcessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledOnJre(JRE.JAVA_8)
public class MultiClientTest extends Thread{
	private final String hostname = "localhost";
	private Server server;
	private static int port = 1239;

    private final Deque<byte[]> messageList = new ConcurrentLinkedDeque<>();


    @Test
	public void testMultiClient() {
		int n = 10;
        Thread[] threads = new Thread[n];
        for(int i=0; i<n; i++) {
            Thread thread = new Thread(new MultiClientTest());
            thread.start();
            threads[i] = thread;
        }

        Assertions.assertAll(() -> {
            for (int i=0; i<n; i++) {
                threads[i].join();
            }
        });
	}

    // testMultiClient executes this with new MultiClientTest() thread
	public void run() {
        Random random = new Random();
        for(int i=0;i<3;i++) {
            // Sleep to make the ordering unpredictable
            Assertions.assertAll(() -> {
                Thread.sleep(random.nextInt(100));
                testSendBatch();
                testSendMessage();
            });
        }
	}

    @BeforeAll
    public void init() {
        Supplier<FrameDelegate> frameProcessorSupplier = () -> new SyslogFrameProcessor((frame) -> messageList.add(frame.relpFrame().payload().toBytes()));

        port = getPort();
        Config config = new Config(port, 4);
        ServerFactory serverFactory = new ServerFactory(config, frameProcessorSupplier);
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

        // 10 threads each: run 3 times 50 msgs of testSendBatch plus 3 times
        // 1 msgs of testSendMessage (1530 messages total)
        Assertions.assertEquals(10 * 3 * 50 + 10 * 3, messageList.size());
    }

	private synchronized int getPort() {
		return ++port;
	}

    private void testSendMessage() {
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
    }

    private void testSendBatch() {
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
    }

}
