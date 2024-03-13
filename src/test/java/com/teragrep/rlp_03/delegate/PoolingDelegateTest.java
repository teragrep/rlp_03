/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021,2024  Suomen Kanuuna Oy
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
package com.teragrep.rlp_03.delegate;

import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.Server;
import com.teragrep.rlp_03.ServerFactory;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.readme.ExampleRelpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PoolingDelegateTest {
    private final static Logger LOGGER = LoggerFactory.getLogger(PoolingDelegateTest.class);

    @Test
    public void testPoolingDelegate() {

        AtomicLong frameDelegates = new AtomicLong();
        AtomicLong frameCount = new AtomicLong();

        Consumer<FrameContext> frameContextConsumer = frameContext -> {
            frameCount.incrementAndGet();
            // make main thread run faster than this, so we get more pooled ones
            while (true) {
                try {
                    Thread.sleep(100L);
                    break;
                } catch (InterruptedException ignored) {

                }
            }
        };

        Supplier<FrameDelegate> frameDelegateSupplier = () -> {
            FrameDelegate defaultDelegate = new DefaultFrameDelegate(frameContextConsumer);
            frameDelegates.incrementAndGet();
            return defaultDelegate;
        };

        try (FrameDelegatePool frameDelegatePool = new FrameDelegatePool(frameDelegateSupplier)) {
            PoolingDelegate poolingDelegate = new PoolingDelegate(frameDelegatePool);
            int port = 11601;

            // return always the same instance
            Supplier<FrameDelegate> poolingDelegateSupplier = () -> poolingDelegate;

            ServerFactory serverFactory = new ServerFactory(new Config(port, 8), poolingDelegateSupplier);

            Server server = serverFactory.create();

            Thread serverThread = new Thread(server);

            serverThread.start();

            server.startup.waitForCompletion();

            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");
            new ExampleRelpClient(port).send("123");

            Thread.sleep(9000);
            server.stop();

            serverThread.join();

            Assertions.assertEquals(1, frameDelegates.get());
            Assertions.assertEquals(1, frameCount.get());

            Thread.sleep(9000);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
