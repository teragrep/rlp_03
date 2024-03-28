package com.teragrep.rlp_03.client;

import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.SocketFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

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
    public void t() throws IOException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        SocketFactory socketFactory = new PlainFactory();
        Supplier<FrameDelegate> frameDelegateSupplier = () -> new FrameDelegate() {
            @Override
            public boolean accept(FrameContext frameContext) {
                LOGGER.info("client got <[{}]>", frameContext.relpFrame());
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

        ConnectContextFactory connectContextFactory = new ConnectContextFactory(
                executorService,
                socketFactory,
                frameDelegateSupplier
        );


        ConnectContext connectContext = connectContextFactory.create(new InetSocketAddress(port));
        connectContext.register(server.eventLoop);

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("this is wip");
    }
}
