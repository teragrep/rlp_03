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

import com.teragrep.rlp_03.config.TLSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static java.nio.channels.SelectionKey.OP_ACCEPT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.function.Supplier;
import com.teragrep.rlp_03.config.Config;


/**
 * Fires up a new Thread to process per connection sockets.
 */
public class SocketEventLoop implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketEventLoop.class);
    private final Config config;
    private final TLSConfig tlsConfig;

    private boolean shouldStop = false;

    private int currentThread = -1; // used to select next thread for accepting
    private final List<Selector> messageSelectorList = new ArrayList<>();
    private final List<Thread> messageSelectorThreadList = new ArrayList<>();

    private final Map<Long, RelpClientSocket> socketMap = new HashMap<>();
    private long nextSocketId = 0;

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final ConnectionEventCompletionService connectionEventCompletionService;



    public SocketEventLoop(
            Config config,
            TLSConfig tlsConfig,
            Supplier<FrameProcessor> frameProcessorSupplier
    ) {
        this.config = config;
        this.tlsConfig = tlsConfig;
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.connectionEventCompletionService = new ConnectionEventCompletionService();
    }

    @Override
    public void run() {

        try (Selector acceptSelector = Selector.open()) {
            try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
                serverSocket.socket().setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(config.port));
                serverSocket.configureBlocking(false);
                serverSocket.register(acceptSelector, OP_ACCEPT);

                if (config.numberOfThreads > 1) {
                    // run as multi-threaded
                    runMultiThreaded(acceptSelector, serverSocket);
                } else {
                    // run as a single-thread
                    runSingleThreaded(acceptSelector, serverSocket);
                }
            }
        }
        catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    /**
     * runSingleThreaded uses only acceptSelector to process both new
     * connection accepts and the same one to process existing connection
     * reads, writes and closes.
     */
    private void runSingleThreaded(Selector acceptSelector, ServerSocketChannel serverSocket) {
        // add acceptSelector to be used as established connection selector
        messageSelectorList.add(acceptSelector);
        while (!shouldStop) {
            try {
                int readReady = acceptSelector.select(500); // TODO add configurable wait

                if (readReady > 0) {
                    Set<SelectionKey> keys = acceptSelector.selectedKeys();

                    for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext(); ) {
                        SelectionKey selectionKey = iter.next();
                        RelpClientSocket clientRelpSocket = (RelpClientSocket) selectionKey.attachment();
                        int readyOps = selectionKey.readyOps();

                        if (clientRelpSocket == null) {
                            processAccept(serverSocket, selectionKey);
                        } else {
                            connectionEventCompletionService.call(
                                    selectionKey
                            );
                        }

                        iter.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            acceptSelector.close();
            serverSocket.close();
        } catch(IOException e) {
            LOGGER.warn("Failed to stop server: ", e);
        }
    }

    /**
     * runMultiThreaded uses separate threads, one for processing accepts
     * with acceptSelector and multiple for processing established connection
     * reads, writes and closes.
     */
    private void runMultiThreaded(Selector acceptSelector, ServerSocketChannel serverSocket) {
        // create established connection selectors, one per thread
        for (int threadId = 0 ; threadId < config.numberOfThreads ; threadId++ ) {
            try {
                Selector messageSelector = Selector.open();

                messageSelectorList.add(messageSelector);

                int finalThreadId = threadId;
                Thread messageThread = new Thread(() -> {
                    try {
                        while (!shouldStop) {
                            runMTMessageSelector(messageSelector);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, "RELP-MP-" + finalThreadId);

                messageSelectorThreadList.add(messageThread);

                messageThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // create new connection accept thread
        Thread accepterThread = new Thread(() -> {
            try {
                while (!shouldStop) {
                    runMTAcceptSelector(acceptSelector, serverSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "RELP-AC");

        accepterThread.start();

        // wait for them to exit
        try {
            accepterThread.join();
            for (Thread thread : messageSelectorThreadList) {
                thread.join();
            }
            acceptSelector.close();
            serverSocket.close();
        } catch (InterruptedException | IOException e) {
            // FIXME
            e.printStackTrace();
        }
    }


    /*
     * Processes the attached RelpServerSocket if it exists, or takes new connections and creates
     * a RelpServerSocket object for that connection.
     */
    private void runMTAcceptSelector(Selector acceptSelector, ServerSocketChannel serverSocket) {
        try {
            int readReady = acceptSelector.select(500); // TODO add configurable wait

            if (readReady > 0) {
                Set<SelectionKey> keys = acceptSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                    SelectionKey selectionKey = iter.next();
                    RelpClientSocket clientRelpSocket = (RelpClientSocket) selectionKey.attachment();
                    int readyOps = selectionKey.readyOps();

                    if (clientRelpSocket == null) {
                        processAccept(serverSocket, selectionKey);
                    }

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * processAccept is shared between MultiThread and SingleThread code
     *
     * @param serverSocket
     * @param selectionKey
     * @throws IOException
     */
    private void processAccept(ServerSocketChannel serverSocket, SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel socketChannel = serverSocket.accept();

            // new socket
            RelpClientSocket socket;
            if (tlsConfig.useTls) {
                socket = new RelpClientTlsSocket(
                        socketChannel,
                        frameProcessorSupplier.get(),
                        tlsConfig.getSslContext(),
                        tlsConfig.getSslEngineFunction()
                );

            } else {
                socket =
                        new RelpClientPlainSocket(socketChannel,
                                frameProcessorSupplier.get());
            }

            socket.setSocketId(nextSocketId++);


            socketMap.put(socket.getSocketId(), socket);

            // get next handler for this connection
            if (currentThread < config.numberOfThreads - 1) {
                currentThread++;
            } else {
                currentThread = 0;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("socketProcessor> messageSelectorList <{}> currentThread <{}>",
                        messageSelectorList.size(), currentThread
                );
            }

            // non-blocking
            socketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            socketChannel.register(
                    messageSelectorList.get(currentThread),
                    SelectionKey.OP_READ,
                    socket
            );
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("socketProcessor.putNewSockets> exit with socketMap size <{}>", socketMap.size());
            }
        }
    }

    private void runMTMessageSelector(Selector messageSelector) {
        try {
            int readReady = messageSelector.select(500); // TODO add configurable wait

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("runMTMessageSelector> enter with socketMap size <{}> ready <{}>",
                        socketMap.size(), readReady
                );
            }

            if (readReady > 0) {
                Set<SelectionKey> keys = messageSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext(); ) {
                    SelectionKey selectionKey = iter.next();


                    connectionEventCompletionService.call(
                            selectionKey
                    );


                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void stop() {
        this.shouldStop = true;
    }
}

