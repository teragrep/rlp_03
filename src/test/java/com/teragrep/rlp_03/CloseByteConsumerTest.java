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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CloseByteConsumerTest {
    private final String hostname = "localhost";
    private Server server;
    private static int port = 1241;
    private final List<byte[]> messageList = new LinkedList<>();
    private AtomicBoolean closed = new AtomicBoolean();

    class AutoCloseableByteConsumer implements Consumer<FrameContext>, AutoCloseable {
        @Override
        public void accept(FrameContext relpFrameServerRX) {
            messageList.add(relpFrameServerRX.relpFrame().payload().toBytes());
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    public void init() {
        port = getPort();
        Config config = new Config(port, 1);

        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor(new AutoCloseableByteConsumer()));
        Assertions.assertAll(() -> {
            server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();
            server.startup.waitForCompletion();
        });
    }

    public void cleanup() {
        server.stop();
    }

    private synchronized int getPort() {
        return ++port;
    }

    @Test
    public void testSendMessage() {
        init(); // starts server

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

        Assertions.assertAll(() -> Thread.sleep(100)); // closure on the server-side is not synchronized to disconnect

        cleanup(); // closes

        Assertions.assertTrue(closed.get());

        // clear received list
        messageList.clear();
        closed.set(false);
    }
}
