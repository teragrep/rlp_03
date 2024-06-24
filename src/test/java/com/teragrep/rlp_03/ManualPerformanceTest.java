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
package com.teragrep.rlp_03;

import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.net_01.server.Server;
import com.teragrep.net_01.server.ServerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ManualPerformanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualPerformanceTest.class);
    final AtomicLong recordCount = new AtomicLong();

    @Test // for testing with manual tools
    @EnabledIfSystemProperty(
            named = "runServerPerformanceTest",
            matches = "true"
    )
    public void runServerTest() throws InterruptedException, IOException {
        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        EventLoop eventLoop = eventLoopFactory.create();

        Thread eventLoopThread = new Thread(eventLoop);
        eventLoopThread.start();

        int threads = Integer.parseInt(System.getProperty("ServerPerformanceTestThreads", "8"));
        int port = Integer.parseInt(System.getProperty("ServerPerformanceTestPort", "1601"));

        LOGGER.info("Starting ManualPerformanceTest with threads <{}> at port <{}>", threads, port);

        Supplier<FrameDelegate> frameDelegateSupplier = () -> {
            LOGGER.info("requested a new frameDelegate instance ");
            return new DefaultFrameDelegate(frameContext -> {
                recordCount.incrementAndGet();
            });
        };

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                threads,
                threads,
                Long.MAX_VALUE,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                threadPoolExecutor,
                new PlainFactory(),
                new FrameDelegationClockFactory(frameDelegateSupplier)
        );
        Server server = serverFactory.create(port);

        final Reporter reporter = new Reporter(server, recordCount, threadPoolExecutor);

        Thread reporterThread = new Thread(reporter);
        reporterThread.start();

        Thread.sleep(Long.MAX_VALUE);

        eventLoop.stop();
        threadPoolExecutor.shutdown();
        Assertions.assertAll(eventLoopThread::join);
        reporterThread.join();
    }

    private static class Reporter implements Runnable {

        private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);

        final Server server;

        final AtomicBoolean stop = new AtomicBoolean();

        final long interval = 5000;
        final ThreadPoolExecutor threadPoolExecutor;
        final AtomicLong recordCount;

        public Reporter(Server server, AtomicLong recordCount, ThreadPoolExecutor threadPoolExecutor) {
            this.server = server;
            this.recordCount = recordCount;
            this.threadPoolExecutor = threadPoolExecutor;
        }

        @Override
        public void run() {
            while (!stop.get()) {
                long start = recordCount.get();

                try {
                    Thread.sleep(interval);
                }
                catch (InterruptedException e) {
                    continue;
                }
                long end = recordCount.get();

                long rate = (end - start) / (interval / 1000);

                LOGGER
                        .info(
                                "Current records per second rate <{}>, threads <{}>, tasksQueue.size <{}>", rate,
                                threadPoolExecutor.getActiveCount(), threadPoolExecutor.getQueue().size()
                        );
            }
        }
    }

}
