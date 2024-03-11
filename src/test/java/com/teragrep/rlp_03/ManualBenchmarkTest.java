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
import com.teragrep.rlp_03.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import com.teragrep.rlp_09.RelpFlooder;
import com.teragrep.rlp_09.RelpFlooderConfig;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ManualBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManualBenchmarkTest.class);
    private final int port = 1601;
    private final int testDuration = 60;

    @ParameterizedTest
    @EnabledIfSystemProperty(named = "runManualBenchmarkTest", matches = "true")
    @CsvSource({"1,1", "2,2", "4,4", "8,8", "1,4", "4,1", "2,4", "4,2"}) // Pairs of flooderThreads:serverThreads
    public void runBenchmarkTest(int flooderThreads, int serverThreads) throws IOException {
        LOGGER.info("Running <{}> server and <{}> input threads test for <{}> seconds", flooderThreads, serverThreads, testDuration);
        RelpFlooderConfig relpFlooderConfig = new RelpFlooderConfig();
        relpFlooderConfig.setPort(port);
        relpFlooderConfig.setThreads(flooderThreads);
        RelpFlooder relpFlooder = new RelpFlooder(relpFlooderConfig);
        Config config = new Config(port, serverThreads);

        FrameContextConsumer frameContextConsumer = new FrameContextConsumer();
        Supplier<FrameDelegate> frameDelegateSupplier = () -> new DefaultFrameDelegate(frameContextConsumer);

        ServerFactory serverFactory = new ServerFactory(config, frameDelegateSupplier);
        Server server = serverFactory.create();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                relpFlooder.stop();
            }
        }, testDuration*1000);

        Thread serverThread = new Thread(server);
        serverThread.start();
        relpFlooder.start();
        server.stop();
        // Some magical numbers for aligning outputs, ;;__;;
        LOGGER.info(String.format("%22s%14s", "Flooder", "Server"));
        LOGGER.info(String.format("%-15s%,-15d%,d", "Threads", flooderThreads, serverThreads));
        LOGGER.info(String.format("%-15s%,-15d%,d", "Records", relpFlooder.getTotalRecordsSent(), frameContextConsumer.receivedRecords.get()));
        LOGGER.info(String.format("%-15s%,-15.2f%,.2f", "Megabytes", relpFlooder.getTotalBytesSent() / 1024f / 1024f, frameContextConsumer.receivedBytes.get() / 1024f / 1024f));
        LOGGER.info(String.format("%-15s%,-15.2f%,.2f", "Megabytes/s", relpFlooder.getTotalBytesSent() / 1024f / 1024f / testDuration, frameContextConsumer.receivedBytes.get() / 1024f / 1024f / testDuration));
        LOGGER.info(String.format("%-15s%,-15d%,d", "RPS", relpFlooder.getTotalRecordsSent() / testDuration, frameContextConsumer.receivedRecords.get() / testDuration));
        LOGGER.info(String.format("%-15s%,-15d%,d", "RPS/thread", relpFlooder.getTotalRecordsSent() / testDuration / flooderThreads, frameContextConsumer.receivedRecords.get() / testDuration / serverThreads));
    }

    private static class FrameContextConsumer implements Consumer<FrameContext> {
        final AtomicLong receivedRecords = new AtomicLong();
        final AtomicLong receivedBytes = new AtomicLong();

        @Override
        public void accept(FrameContext frameContext) {
            receivedRecords.incrementAndGet();
            receivedBytes.addAndGet(frameContext.relpFrame().payload().size());
        }

        @Override
        public Consumer<FrameContext> andThen(Consumer<? super FrameContext> after) {
            return Consumer.super.andThen(after);
        }
    }
}