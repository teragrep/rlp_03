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

import java.io.IOException;

/**
 * A class that starts the server connection to the client. Fires up a new thread
 * for the Socket Processor.
 */
public class Server
{
    private SocketProcessor socketProcessor = null;

    private Thread processorThread;

    private final FrameProcessor frameProcessor;

    private int port = 0;

    private int numberOfThreads = 1;


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public Server(int port, FrameProcessor frameProcessor) {
        this.port = port;
        this.frameProcessor = frameProcessor;
    }

    public void start() throws IOException {
        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "server.start> entry ");
        }

        socketProcessor = new SocketProcessor(port, frameProcessor, numberOfThreads);

        processorThread = new Thread(socketProcessor);

        processorThread.start();

        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "server.start> exit ");
        }

    }
    public void stop() throws InterruptedException {

        if(socketProcessor != null) {
            socketProcessor.stop();
        }

        if (processorThread != null) {
            if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
                System.out.println("processorThread.join()");
            }
            processorThread.join();
        }
    }
}