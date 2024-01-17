package com.teragrep.rlp_03;

import com.teragrep.rlp_03.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.io.IOException;

public class ServerShutdownTest {
    @Test
    public void testServerShutdownSingleThread() throws IOException, InterruptedException {
        Config config = new Config(10601, 1);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor(System.out::println));
        Server server = serverFactory.create();

        Thread serverThread = new Thread(server);
        serverThread.start();

        server.startup.waitForCompletion();

        server.stop();

        serverThread.join();
    }

    @Test
    public void testServerShutdownMultiThread() throws IOException, InterruptedException {
        Config config = new Config(10601, 8);
        ServerFactory serverFactory = new ServerFactory(config, new SyslogFrameProcessor(System.out::println));
        Server server = serverFactory.create();

        Thread serverThread = new Thread(server);
        serverThread.start();

        server.startup.waitForCompletion();

        server.stop();

        serverThread.join();
    }
}
