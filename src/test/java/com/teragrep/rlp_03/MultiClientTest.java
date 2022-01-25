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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

public class MultiClientTest extends Thread{
	private final String hostname = "localhost";
	private Server server;
	private static int port = 1239;

//	@Test
	public void testMultiClient() throws InterruptedException, IllegalStateException, IOException, TimeoutException {
		int n = 10;
        Thread threads[] = new Thread[n];
        for(int i=0; i<n; i++) {
            Thread thread = new Thread(new MultiClientTest());
            thread.start();
            threads[i] = thread;
        }

        for (int i=0; i<n; i++) {
            threads[i].join();
        }
	}

	public void run() {
        Random random = new Random();

        for(int i=0;i<3;i++) {
//             Sleep to make the ordering unpredictable
            try {
                Thread.sleep(random.nextInt(100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
            	testSendBatch();
//            	testSendMessage();
			} catch (IOException | TimeoutException | IllegalStateException  e) {
				e.printStackTrace();
			}
        }
	}

    @Before
    public void init() throws IOException, InterruptedException {
        port = getPort();
        server = new Server(port, new SyslogFrameProcessor(System.out::println));
        server.start();
        Thread.sleep(10);
        System.out.println("Started server at " + hostname + " port " + port);
    }

    @After
    public void cleanup() throws InterruptedException {
        System.out.println("Closing " + hostname + " port " + port);
        server.stop();
    }

	private synchronized int getPort() {
		return ++port;
	}

    public void testSendMessage() throws IOException, TimeoutException {
        RelpConnection relpSession = new RelpConnection();
        relpSession.connect(hostname, port);
        String msg = "<14>1 2020-05-15T13:24:03.603Z CFE-16 capsulated - - [CFE-16-metadata@48577 authentication_token=\"AUTH_TOKEN_11111\" channel=\"CHANNEL_11111\" time_source=\"generated\"][CFE-16-origin@48577] \"Hello, world!\"\n";
        byte[] data = msg.getBytes("UTF-8");
        RelpBatch batch = new RelpBatch();
        long reqId = batch.insert(data);
        relpSession.commit(batch);
        // verify successful transaction
        assertTrue(batch.verifyTransaction(reqId));
        relpSession.disconnect();
    }

    public void testSendBatch() throws IllegalStateException, IOException, TimeoutException {
        System.out.println("i'd like to connect to " + hostname + " port " + port);
        RelpConnection relpSession = new RelpConnection();
        relpSession.connect(hostname, port);
        String msg = "Hello, world!";
        byte[] data = msg.getBytes("UTF-8");
        int n = 50;
        RelpBatch batch = new RelpBatch();
        for (int i = 0; i < n; i++) {
            batch.insert(data);
        }
        relpSession.commit(batch);
        assertTrue(batch.verifyTransactionAll());
        relpSession.disconnect();
    }

}
