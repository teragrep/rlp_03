package com.teragrep.rlp_03;

import com.teragrep.rlp_03.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ServerShutdownTest {
    @Test
    public void testServerShutdownSingleThread() {
        Config config = new Config(10601, 1);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor(System.out::println));
        Assertions.assertAll(() -> {
            Server server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();

            server.stop();

            serverThread.join();
        });
    }

    @Test
    public void testServerShutdownMultiThread() {
        Config config = new Config(10601, 8);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor(System.out::println));
        Assertions.assertAll(() -> {
            Server server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();

            server.stop();

            serverThread.join();
        });
    }
}
