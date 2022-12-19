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

import static java.nio.channels.SelectionKey.OP_ACCEPT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * Fires up a new Thread to process per connection sockets.
 */
public class SocketProcessor implements Runnable {

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

    private final FrameProcessor frameProcessor;

    public SocketProcessor(int port, FrameProcessor frameProcessor,
                           int numberOfThreads) throws IOException {
        this.port = port;
        this.frameProcessor = frameProcessor;
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
    }

    public void run() {
        for (int threadId = 0 ; threadId < numberOfThreads ; threadId++ ) {
            try {
                Selector messageSelector = Selector.open();

                messageSelectorList.add(messageSelector);

                int finalThreadId = threadId;
                Thread messageThread = new Thread(() -> {
                    try {
                        while (!shouldStop) {
                            //System.out.println("grande select ");
                            runMessageSelector(messageSelector, finalThreadId);
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

        Thread accepterThread = new Thread(() -> {
            try {
                while (!shouldStop) {
                    //System.out.println("grande select ");
                    runAcceptSelector();
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
    private void runAcceptSelector() {
        try {
            int readReady = acceptSelector.select(500); // TODO add configurable wait

            if (readReady > 0) {
                Set<SelectionKey> keys = acceptSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                    SelectionKey selectionKey = iter.next();
                    RelpServerSocket clientRelpSocket = (RelpServerSocket) selectionKey.attachment();
                    int readyOps = selectionKey.readyOps();

                    if (clientRelpSocket == null) {
                        if (selectionKey.isAcceptable()) {
                            // create the client socket for a newly received connection
                            SocketChannel socketChannel = serverSocket.accept();

                            // new socket
                            RelpServerSocket socket =
                                    new RelpServerPlainSocket(socketChannel,
                                            frameProcessor);

                            socket.setSocketId(nextSocketId++);


                            socketMap.put(socket.getSocketId(), socket);

                            // get next handler for this connection
                            if (currentThread < numberOfThreads - 1) {
                                currentThread++;
                            } else {
                                currentThread = 0;
                            }

                            if (System.getenv("RELP_SERVER_DEBUG") != null) {
                                System.out.println("socketProcessor> messageSelectorList: " + messageSelectorList.size()
                                        + " currentThread: " + currentThread);
                            }

                            // non-blocking
                            socketChannel.configureBlocking(false);

                            // all client connected sockets start in OP_READ
                            SelectionKey key = socketChannel.register(
                                    messageSelectorList.get(currentThread),
                                    SelectionKey.OP_READ,
                                    socket
                            );

                            if (System.getenv("RELP_SERVER_DEBUG") != null) {
                                System.out.println("socketProcessor.putNewSockets> exit with socketMap size: " + socketMap.size());
                            }
                        }
                    }

                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runMessageSelector(Selector messageSelector, int finalThreadId) {
        try {
            int readReady = messageSelector.select(500); // TODO add configurable wait
            /* TODO move behind a final variable to JIT it out, "slow code"
            if (System.getenv("RELP_SERVER_DEBUG") != null) {
                System.out.println( "runMessageSelector> enter with socketMap" +
                        " size: " + socketMap.size()
                + " ready: " + readReady
                        );
            }
             */
            if (readReady > 0) {
                Set<SelectionKey> keys = messageSelector.selectedKeys();

                for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                    SelectionKey selectionKey = iter.next();
                    RelpServerSocket clientRelpSocket = (RelpServerSocket) selectionKey.attachment();
                    int readyOps = selectionKey.readyOps();

                    if (clientRelpSocket != null) {
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
                            //System.out.println("OP_READ @ " + finalThreadId);
                            currentOps = clientRelpSocket.processRead(currentOps);
                        }


                        if (currentOps != 0) {
                            //System.out.println("changing ops: " + currentOps);
                            selectionKey.interestOps(currentOps);
                        }
                        else {
                            // No operations indicates we are done with this one
                            //System.out.println("changing ops (closing): " + currentOps);
                            selectionKey.attach(null);
                            selectionKey.channel().close();
                            selectionKey.cancel();

                            //this.socketMap.remove(socket);
                        }
                    }

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

