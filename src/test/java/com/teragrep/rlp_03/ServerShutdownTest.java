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
        Server server = new Server(config, new SyslogFrameProcessor(System.out::println));

        Thread serverThread = new Thread(server);
        serverThread.start();

        server.startup.waitForCompletion();

        server.stop();

        serverThread.join();
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8)
    public void testServerShutdownMultiThread() throws IOException, InterruptedException {
        Config config = new Config(10601, 8);
        Server server = new Server(config, new SyslogFrameProcessor(System.out::println));

        Thread serverThread = new Thread(server);
        serverThread.start();

        server.startup.waitForCompletion();

        server.stop();

        serverThread.join();
    }
}
