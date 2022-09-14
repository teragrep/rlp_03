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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Fires up a new Thread to process per connection sockets.
 */
public class SocketProcessor implements Runnable {

    private boolean shouldStop = false;

    private final Map<Long, RelpServerSocket> socketMap = new HashMap<>();

//    private Queue<SelectionKey> writeKeysQueue = new ArrayBlockingQueue<>(1024);

    private final Selector selector;
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

    public SocketProcessor(int port, FrameProcessor frameProcessor) throws IOException {
        this.port = port;
        this.frameProcessor = frameProcessor;
        this.selector = Selector.open();
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.socket().setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(this.port));
        this.serverSocket.configureBlocking(false);
        this.serverSocket.register(selector, OP_ACCEPT);
    }

    public void run() {
        Thread messageProcessor = new Thread(() -> {
            try {
                while (!shouldStop) {
                    //System.out.println("grande select ");
                    grandSelector();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        messageProcessor.start();

        // wait for them to exit
        try {
            messageProcessor.join();
            serverSocket.close();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }


    /*
     * Processes the attached RelpServerSocket if it exists, or takes new connections and creates
     * a RelpServerSocket object for that connection.
     */
    private void grandSelector() {
        /*

        DO operation here
        return value indicates if ops should be changed
        i.e. after reads write may be desired.
        i.e. after writes, write may be still desired or not desired.
        move accept here as OP_ACCEPT is an operation that changes to read as well

        -> more simple, more efficient, keys can not be changed when in select
        otherwise https://stackoverflow.com/questions/11523471/java-selectionkey-interestopsint-not-thread-safe
        says that the select() will not consider any changes and performance issues arise

        how about perf? well perf says that we return anyway when incomplete reads or writes so threads always get max perf

        some nice design they got in
        https://github.com/EsotericSoftware/kryonet/blob/master/src/com/esotericsoftware/kryonet/Server.java
        although it does not seem to do multithread, however  changing connect in to return changed ops should do much, same for readOperatiom and writeOperation
        see
        https://github.com/EsotericSoftware/kryonet/blob/03a135e2039bd7eb20e436ad70539238563d15a4/src/com/esotericsoftware/kryonet/TcpConnection.java#L84

        it seems silly that they don't change the ops in the selector but pass internals (selector) to the object and that won't work very well

        selector should not return the same connection anyway for read or
        write in parallel to two different threads and well we can check if it does by guarding
        the RelpServerSocket with a read and a write lock and cry loud for debugging to see if that happens

        sounds too easy to be true?
         */

        try {
            // FIXME multithreading does not work
            int readReady = selector.select(500); // TODO add configurable wait

            if (readReady > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();

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
                    else {
                        if (selectionKey.isAcceptable()) {
                            // create the client socket for a newly received connection
                            SocketChannel socketChannel = serverSocket.accept();

                            // new socket
                            RelpServerSocket socket = new RelpServerSocket(socketChannel, frameProcessor);

                            socket.setSocketId(nextSocketId++);

                            socket.getSocketChannel().configureBlocking(false);

                            socketMap.put(socket.getSocketId(), socket);

                            // all client connected sockets start in OP_READ
                            SelectionKey key = socket.getSocketChannel().register(
                                    selector,
                                    SelectionKey.OP_READ
                            );
                            key.attach(socket);
                        }
                        if (System.getenv("RELP_SERVER_DEBUG") != null) {
                            System.out.println( "socketProcessor.putNewSockets> exit with socketMap size: " + socketMap.size());
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

