/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2024  Suomen Kanuuna Oy
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
package com.teragrep.rlp_03.client;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;
import com.teragrep.rlp_03.Server;
import com.teragrep.rlp_03.ServerFactory;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.ConnectionContextImpl;
import com.teragrep.rlp_03.context.InterestOpsImpl;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClientTest {

    // TODO work in progress

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTest.class);


    private final String hostname = "localhost";
    private Server server;
    private final int port = 22601;


    @BeforeAll
    public void init() {
        Config config = new Config(port, 1);
        ServerFactory serverFactory = new ServerFactory(config, () -> new DefaultFrameDelegate((frame) -> LOGGER.info("server got <[{}]>", frame.relpFrame())));
        Assertions.assertAll(() -> {
            server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();
        });
    }

    @AfterAll
    public void cleanup() {
        server.stop();
    }


    @Test
    public void testPowerClient() throws IOException, TimeoutException, InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();

        SocketFactory socketFactory = new PlainFactory();

        Socketish socketish = new Socketish();
        socketish.connect(hostname, port);

        Socket socket = socketFactory.create(socketish.socketChannel);

        ConnectionContext connectionContext = new ConnectionContextImpl(
                executorService,
                socket,
                new InterestOpsImpl(socketish.key),
                new FrameDelegate() {
                    @Override
                    public boolean accept(FrameContext frameContext) {
                        LOGGER.info("client got <[{}]>", frameContext.relpFrame());
                        return true;
                    }

                    @Override
                    public void close() throws Exception {

                    }

                    @Override
                    public boolean isStub() {
                        return false;
                    }
                }
        );

        socketish.attach(connectionContext);


        List<RelpFrameTX> relpFrameTXES = new ArrayList<>();
        RelpFrameTX relpFrameTX = new RelpFrameTX("open", "a hallo yo client".getBytes(StandardCharsets.UTF_8));
        relpFrameTX.setTransactionNumber(1);
        relpFrameTXES.add(relpFrameTX);
        LOGGER.info("sending <{}>", relpFrameTX);
        connectionContext.relpWrite().accept(relpFrameTXES);
        relpFrameTXES.clear();

        RelpFrameTX relpFrameTX2 = new RelpFrameTX("syslog", "yonnes payload".getBytes(StandardCharsets.UTF_8));
        relpFrameTX2.setTransactionNumber(2);
        relpFrameTXES.add(relpFrameTX2);
        LOGGER.info("sending <{}>", relpFrameTX2);
        connectionContext.relpWrite().accept(relpFrameTXES);
        relpFrameTXES.clear();


        RelpFrameTX relpFrameTX3 = new RelpFrameTX("close", "".getBytes(StandardCharsets.UTF_8));
        relpFrameTX3.setTransactionNumber(3);
        relpFrameTXES.add(relpFrameTX3);
        LOGGER.info("sending <{}>", relpFrameTX3);
        connectionContext.relpWrite().accept(relpFrameTXES);
        relpFrameTXES.clear();

        int count = 10;
        while (count > 0) {
            // read responses
            int a = socketish.selector.select(10);
            LOGGER.info("ready keys <{}>", a);
            for (SelectionKey selectionKey : socketish.selector.selectedKeys()) {
                ConnectionContext con = (ConnectionContext) selectionKey.attachment();
                con.handleEvent(selectionKey);
            }
            count--;
        }

    }

    private class Socketish {
        // TODO implement as ClientSocketFactory, returning ClientContexts?

        public final SocketChannel socketChannel;
        public final SelectionKey key;

        public final Selector selector;

        Socketish() throws IOException {
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.socket().setKeepAlive(true);
            socketChannel.configureBlocking(false);
            key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
        }
        public void attach(Object o) {
            key.attach(o);
        }

        public void connect(String hostname, int port) throws TimeoutException, IOException {
            // TODO part of ConnectEvent implements KeyEvent strategy?
            socketChannel.connect(new InetSocketAddress(hostname, port));

            boolean notConnected = true;
            while (notConnected) {
                int nReady = selector.select(500);
                // Woke up without anything to do
                if (nReady == 0) {
                    throw new TimeoutException("connection timed out");
                }
                // It would be possible to skip the whole iterator, but we want to make sure if something else than connect
                // fires then it will be discarded.
                Set<SelectionKey> polledEvents = selector.selectedKeys();
                Iterator<SelectionKey> eventIter = polledEvents.iterator();
                while (eventIter.hasNext()) {
                    SelectionKey currentKey = eventIter.next();
                    if (currentKey.isConnectable()) {
                        if (socketChannel.finishConnect()) {
                            // Connection established
                            notConnected = false;
                        }
                    }
                    eventIter.remove();
                }
            }
            // No need to be longer interested in connect.
            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
            key.interestOps(SelectionKey.OP_READ);
        }
    }


}
