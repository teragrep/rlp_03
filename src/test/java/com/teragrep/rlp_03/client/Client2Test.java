package com.teragrep.rlp_03.client;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.context.frame.RelpFrame;
import com.teragrep.rlp_03.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Client2Test {

    private static final Logger LOGGER = LoggerFactory.getLogger(Client2Test.class);

    private final String hostname = "localhost";
    private Server server;
    private final int port = 23601;


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

    @Test
    public void t() throws IOException, InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        SocketFactory socketFactory = new PlainFactory();


        ConnectContextFactory connectContextFactory = new ConnectContextFactory(
                executorService,
                socketFactory
        );

        // this is for returning ready connection
        TransferQueue<ConnectionContext> readyContexts = new LinkedTransferQueue<>();
        Consumer<ConnectionContext> connectionContextConsumer = connectionContext -> {
            LOGGER.info("connectionContext ready");
            readyContexts.add(connectionContext);
        };


        // TODO perhaps <Integer, Future<Something>> ?
        // FIXME what should be Something, RelpFrame is immediately deallocated after FrameDelegate
        HashMap<Integer, CompletableFuture<String>> pendingTransactions = new HashMap<>();

        FrameDelegate essentialClientDelegate = new FrameDelegate() {
            @Override
            public boolean accept(FrameContext frameContext) {
                LOGGER.info("client got <[{}]>", frameContext.relpFrame());

                // FIXME perhaps not the best place to check this because transmitting party has the responsibility
                if ("200 OK".equals(frameContext.relpFrame().payload().toString())) {
                    int txn = frameContext.relpFrame().txn().toInt();

                    CompletableFuture<String> future = pendingTransactions.remove(txn);

                    if (future == null) {
                        throw new IllegalStateException("txn not pending <[" + txn + "]>");
                    }

                    future.complete(frameContext.relpFrame().payload().toString());
                    LOGGER.info("completed transaction for <[{}]>", txn);
                }

                frameContext.relpFrame().close();

                return true;
            }

            @Override
            public void close() throws Exception {
                LOGGER.info("client FrameDelegate close");
            }

            @Override
            public boolean isStub() {
                return false;
            }
        };


        ConnectContext connectContext = connectContextFactory.create(
                new InetSocketAddress(port),
                essentialClientDelegate,
                connectionContextConsumer
        );
        connectContext.register(server.eventLoop);

        try (ConnectionContext connectionContext = readyContexts.take()) {
            Transmit transmit = new Transmit(connectionContext, pendingTransactions);

            CompletableFuture<String> open = transmit.transmit("open", "a hallo yo client".getBytes(StandardCharsets.UTF_8));
            CompletableFuture<String> syslog = transmit.transmit("syslog", "yonnes payload".getBytes(StandardCharsets.UTF_8));
            CompletableFuture<String> close = transmit.transmit("close", "".getBytes(StandardCharsets.UTF_8));

            try {
                // FIXME open and close should be responded too! FrameDelegate is not coping atm
                //RelpFrame openResponse = open.get();
                //LOGGER.info("openResponse <[{}]>", openResponse);
                String syslogResponse = syslog.get();
                LOGGER.info("syslogResponse <[{}]>", syslogResponse);
                Assertions.assertEquals("200 OK", syslogResponse);
                //RelpFrame closeResponse = close.get();
                //LOGGER.info("closeResponse <[{}]>", closeResponse);
            } catch (ExecutionException executionException) {
                throw new RuntimeException(executionException); // TODO
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        //throw new IllegalStateException("this is wip");
    }

    private class Transmit {

        private final ConnectionContext connectionContext;
        private final AtomicInteger txnCounter;
        HashMap<Integer, CompletableFuture<String>> pendingReplyTransactions;

        Transmit(ConnectionContext connectionContext, HashMap<Integer, CompletableFuture<String>> pendingReplyTransactions) {
            this.connectionContext = connectionContext;
            this.txnCounter = new AtomicInteger();
            this.pendingReplyTransactions = pendingReplyTransactions;
        }

        public CompletableFuture<String> transmit(String command, byte[] data) {
            RelpFrameTX relpFrameTX = new RelpFrameTX(command, data);
            int txn = txnCounter.incrementAndGet();
            relpFrameTX.setTransactionNumber(txn);
            if (pendingReplyTransactions.containsKey(txn)) {
                throw new IllegalStateException("already pending txn <" + txn + ">");
            }
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingReplyTransactions.put(txn, future);
            connectionContext.relpWrite().accept(Collections.singletonList(relpFrameTX));

            return future;
        }
    }
}
