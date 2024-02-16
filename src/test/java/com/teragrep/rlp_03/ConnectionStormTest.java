package com.teragrep.rlp_03;

import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_03.config.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConnectionStormTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMessageTest.class);

    private final String hostname = "localhost";
    private Server server;
    private static int port = 1242;

    private final List<byte[]> messageList = new LinkedList<>();

    @BeforeAll
    public void init() throws InterruptedException, IOException {
        port = getPort();
        Config config = new Config(port, 1);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor((frame) -> messageList.add(frame.relpFrame().payload().toBytes())));
        server = serverFactory.create();

        Thread serverThread = new Thread(server);
        serverThread.start();

        server.startup.waitForCompletion();
    }

    @AfterAll
    public void cleanup() {
        server.stop();
    }

    private synchronized int getPort() {
        return ++port;
    }

    @Test
    public void testOpenAndCloseSession() throws IOException, TimeoutException {
        long count = 10000;
        while (count > 0) {
            RelpConnection relpSession = new RelpConnection();
            relpSession.connect(hostname, port);
            relpSession.disconnect();
            count--;
        }
    }
}
