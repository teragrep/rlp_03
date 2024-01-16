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

import com.teragrep.rlp_03.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ManualPerformanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualTest.class);



    @Test // for testing with manual tools
    @EnabledIfSystemProperty(named="runServerPerformanceTest", matches="true")
    public void runServerTest() throws IOException, InterruptedException {

        final ByteConsumer byteConsumer = new ByteConsumer();

        Config config = new Config(1601, 4);
        Server server = new Server(config, new SyslogFrameProcessor(byteConsumer));

        final Reporter reporter = new Reporter(server, byteConsumer);





        Thread serverThread = new Thread(server);
        serverThread.start();

        Thread reporterThread = new Thread(reporter);
        reporterThread.start();

        serverThread.join();
        reporterThread.join();
    }

    private static class ByteConsumer implements Consumer<byte[]> {

        final AtomicLong atomicLong = new AtomicLong();

        @Override
        public void accept(byte[] bytes) {
            try {
                Thread.sleep(1);
                // LOGGER.info("sleep ok");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            atomicLong.incrementAndGet();
        }
    }

    private static class Reporter implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);

        final Server server;
        final ByteConsumer byteConsumer;

        final AtomicBoolean stop = new AtomicBoolean();

        final long interval = 5000;

        public Reporter(Server server, ByteConsumer byteConsumer) {
            this.server = server;
            this.byteConsumer = byteConsumer;
        }

        @Override
        public void run() {
            while (!stop.get()) {
                long start = byteConsumer.atomicLong.get();

                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    continue;
                }
                long end = byteConsumer.atomicLong.get();

                long rate = (end-start)/(interval/1000);

                LOGGER.info("Current records per second rate <{}>, threads <{}>, tasksQueue.size <{}>",
                        rate,
                        server.executorService.getActiveCount(),
                        server.executorService.getQueue().size()
                );
            }
        }
    }

}
