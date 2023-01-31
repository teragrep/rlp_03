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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.GeneralSecurityException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A class that starts the server connection to the client. Fires up a new thread
 * for the Socket Processor.
 */
public class Server
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private SocketProcessor socketProcessor = null;

    private Thread processorThread;

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final SSLContext sslContext;

    private final Function<SSLContext, SSLEngine> sslEngineFunction;

    private int port = 0;

    private int numberOfThreads = 1;

    private final boolean useTls;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setNumberOfThreads(int numberOfThreads) {
        String javaVersion =
                ManagementFactory.getRuntimeMXBean().getSpecVersion();
        if ("1.8".equals(javaVersion) && numberOfThreads > 1) {
            throw new IllegalArgumentException("Java version " + javaVersion +
                    " is unsupported for multi-thread processing");
        }
        this.numberOfThreads = numberOfThreads;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public Server(int port, FrameProcessor frameProcessor) {
        this.port = port;
        this.frameProcessorSupplier = () -> frameProcessor;

        // tls
        this.useTls = false;
        this.sslContext = null;
        this.sslEngineFunction = null;
    }

    public Server(int port, Supplier<FrameProcessor> frameProcessorSupplier) {
        this.port = port;
        this.frameProcessorSupplier = frameProcessorSupplier;

        // tls
        this.useTls = false;
        this.sslContext = null;
        this.sslEngineFunction = null;
    }

    public Server(
            int port,
            FrameProcessor frameProcessor,
            SSLContext sslContext,
            Function<SSLContext, SSLEngine> sslEngineFunction
    ) {
        this.port = port;
        this.frameProcessorSupplier = () -> frameProcessor;

        // tls
        this.useTls = true;
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;
    }

    public Server(
            int port,
            Supplier<FrameProcessor> frameProcessorSupplier,
            SSLContext sslContext,
            Function<SSLContext, SSLEngine> sslEngineFunction
    ) {
        this.port = port;
        this.frameProcessorSupplier = frameProcessorSupplier;

        // tls
        this.useTls = true;
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;
    }

    public void start() throws IOException {
        LOGGER.trace( "server.start> entry ");

        if (useTls) {
            socketProcessor = new SocketProcessor(
                    port,
                    frameProcessorSupplier,
                    numberOfThreads,
                    sslContext,
                    sslEngineFunction
            );
        } else {
            socketProcessor = new SocketProcessor(
                    port,
                    frameProcessorSupplier,
                    numberOfThreads
            );
        }

        processorThread = new Thread(socketProcessor);

        processorThread.start();

        LOGGER.trace( "server.start> exit ");

    }
    public void stop() throws InterruptedException {

        if(socketProcessor != null) {
            socketProcessor.stop();
        }

        if (processorThread != null) {
            LOGGER.trace("processorThread.join()");
            processorThread.join();
        }
    }
}