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

import static java.nio.channels.SelectionKey.OP_ACCEPT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fires up a new Thread to process per connection sockets.
 */
public class SocketProcessor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketProcessor.class);

    private boolean shouldStop = false;

    private final Map<Long, RelpServerSocket> socketMap = new HashMap<>();

    private final Selector acceptSelector;

    private final int numberOfThreads;
    private int currentThread = -1; // used to select next thread for accepting
    private final List<Selector> messageSelectorList = new ArrayList<>();
    private final List<Thread> messageSelectorThreadList = new ArrayList<>();

    private long nextSocketId = 0;

    private int readTimeout = 1000;
    private int writeTimeout = 1000;

    private final boolean useTls;

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public long getNextSocketId() {
        return nextSocketId;
    }

    public void setNextSocketId(long nextSocketId) {
        this.nextSocketId = nextSocketId;
    }

    private final int port;
    private final ServerSocketChannel serverSocket;

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final SSLContext sslContext;
    private final Function<SSLContext, SSLEngine> sslEngineFunction;

    public SocketProcessor(int port, Supplier<FrameProcessor> frameProcessorSupplier,
                           int numberOfThreads) throws IOException {
        this.port = port;
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.acceptSelector = Selector.open();
        if (numberOfThreads < 1) {
            throw new IllegalArgumentException("must use at least one message" +
                    " processor thread");
        }
        this.numberOfThreads = numberOfThreads;
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.socket().setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(this.port));
        this.serverSocket.configureBlocking(false);
        this.serverSocket.register(acceptSelector, OP_ACCEPT);

        // tls
        this.useTls = false;
        this.sslContext = null;
        this.sslEngineFunction = null;
    }

    public SocketProcessor(int port, Supplier<FrameProcessor> frameProcessorSupplier,
                           int numberOfThreads,
                           SSLContext sslContext,
                           Function<SSLContext, SSLEngine> sslEngineFunction) throws IOException {
        this.port = port;
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.acceptSelector = Selector.open();
        if (numberOfThreads < 1) {
            throw new IllegalArgumentException("must use at least one message" +
                    " processor thread");
        }
        this.numberOfThreads = numberOfThreads;
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.socket().setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(this.port));
        this.serverSocket.configureBlocking(false);
        this.serverSocket.register(acceptSelector, OP_ACCEPT);
        this.useTls = true;
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;
    }

    public void run() {
        if (numberOfThreads > 1) {
            // run as multi-threaded
            runMultiThreaded();
        }
        else {
            // run as a single-thread
            runSingleThreaded();
        }
    }

    /**
     * runSingleThreaded uses only acceptSelector to process both new
     * connection accepts and the same one to process existing connection
     * reads, writes and closes.
     */
    private void runSingleThreaded() {
        // add acceptSelector to be used as established connection selector
        messageSelectorList.add(acceptSelector);
        while (!shouldStop) {
            try {
                int readReady = acceptSelector.select(500); // TODO add configurable wait

                if (readReady > 0) {
                    Set<SelectionKey> keys = acceptSelector.selectedKeys();

                    for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext(); ) {
                        SelectionKey selectionKey = iter.next();
                        RelpServerSocket clientRelpSocket = (RelpServerSocket) selectionKey.attachment();
                        int readyOps = selectionKey.readyOps();

                        if (clientRelpSocket == null) {
                            processAccept(selectionKey);
                        } else {
                            processReadWriteClose(
                                    selectionKey,
                                    clientRelpSocket,
                                    readyOps
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
            for(Selector selector : messageSelectorList) {
                selector.close();
            }
            serverSocket.socket().close();
        } catch(IOException e) {
            LOGGER.warn("Failed to stop server: ", e);
        }
    }

    /**
     * runMultiThreaded uses separate threads, one for processing accepts
     * with acceptSelector and multiple for processing established connection
     * reads, writes and closes.
     */
    private void runMultiThreaded() {
        // create established connection selectors, one per thread
        for (int threadId = 0 ; threadId < numberOfThreads ; threadId++ ) {
            try {
                Selector messageSelector = Selector.open();

                messageSelectorList.add(messageSelector);

                int finalThreadId = threadId;
                Thread messageThread = new Thread(() -> {
                    try {
                        while (!shouldStop) {
                            runMTMessageSelector(messageSelector, finalThreadId);
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
                    runMTAcceptSelector();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "RELP-AC");

        accepterThread.start();

        // wait for them to exit
        try {
            accepterThread.join();
            for (Selector selector : messageSelectorList) {
                selector.close();
            }
            for (Thread thread : messageSelectorThreadList) {
                thread.join();
            }
            acceptSelector.close();
            serverSocket.socket().close();
        } catch (InterruptedException | IOException e) {
            // FIXME
            e.printStackTrace();
        }
    }


    /*
     * Processes the attached RelpServerSocket if it exists, or takes new connections and creates
     * a RelpServerSocket object for that connection.
     */
    private void runMTAcceptSelector() {
        try {
            int readReady = acceptSelector.select(500); // TODO add configurable wait

            if (readReady > 0) {
                Set<SelectionKey> keys = acceptSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                    SelectionKey selectionKey = iter.next();
                    RelpServerSocket clientRelpSocket = (RelpServerSocket) selectionKey.attachment();
                    int readyOps = selectionKey.readyOps();

                    if (clientRelpSocket == null) {
                        processAccept(selectionKey);
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
     * @param selectionKey
     * @throws IOException
     */
    private void processAccept(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel socketChannel = serverSocket.accept();

            // new socket
            RelpServerSocket socket;
            if (useTls) {
                socket = new RelpServerTlsSocket(
                        socketChannel,
                        frameProcessorSupplier.get(),
                        sslContext,
                        sslEngineFunction
                );

            } else {
                socket =
                        new RelpServerPlainSocket(socketChannel,
                                frameProcessorSupplier.get());
            }

            socket.setSocketId(nextSocketId++);


            socketMap.put(socket.getSocketId(), socket);

            // get next handler for this connection
            if (currentThread < numberOfThreads - 1) {
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

    private void runMTMessageSelector(Selector messageSelector, int finalThreadId) {
        try {
            int readReady = messageSelector.select(500); // TODO add configurable wait

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("runMTMessageSelector> enter with socketMap size <{}> ready <{}>",
                        socketMap.size(), readReady
                );
            }

            if (readReady > 0) {
                Set<SelectionKey> keys = messageSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                    SelectionKey selectionKey = iter.next();
                    RelpServerSocket clientRelpSocket = (RelpServerSocket) selectionKey.attachment();
                    int readyOps = selectionKey.readyOps();

                    if (clientRelpSocket != null) {
                        processReadWriteClose(
                                selectionKey,
                                clientRelpSocket,
                                readyOps
                        );
                    }

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * processReadWriteClose is shared between MultiThread and SingleThread code
     * @param selectionKey
     * @param clientRelpSocket
     * @param readyOps
     * @throws IOException
     */
    private void processReadWriteClose(
            SelectionKey selectionKey,
            RelpServerSocket clientRelpSocket,
            int readyOps
    ) throws IOException {
        /*
        operations are toggled based on the return values of the socket
        meaning: the internal status of the parser.
        */
        int currentOps = selectionKey.interestOps();

        // writes become first
        if ((readyOps & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
            currentOps = clientRelpSocket.processWrite(currentOps);
        }

        if ((readyOps & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
            //LOGGER.trace("OP_READ @ " + finalThreadId);
            currentOps = clientRelpSocket.processRead(currentOps);
        }


        if (currentOps != 0) {
            //LOGGER.trace("changing ops: " + currentOps);
            selectionKey.interestOps(currentOps);
        } else {
            // No operations indicates we are done with this one
            //LOGGER.trace("changing ops (closing): " + currentOps);
            try {
                // call close on socket so frameProcessor can cleanup
                clientRelpSocket.close();
            } catch (Exception e) {
                LOGGER.trace("clientRelpSocket.close(); threw", e);
            }
            selectionKey.attach(null);
            selectionKey.channel().close();
            selectionKey.cancel();

            this.socketMap.remove(clientRelpSocket.getSocketId());
        }
    }


    public void stop() {
        this.shouldStop = true;
    }
}

