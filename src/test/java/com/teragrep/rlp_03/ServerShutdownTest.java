package com.teragrep.rlp_03;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ServerShutdownTest {
    @Test
    public void testServerShutdownSingleThread() throws IOException, InterruptedException {
        Server server = new Server(10601, new SyslogFrameProcessor(System.out::println));
        server.start();
        server.stop();
        server.start();
        server.stop();
    }

    @Test
    public void testServerShutdownMultiThread() throws IOException, InterruptedException {
        Server server = new Server(10601, new SyslogFrameProcessor(System.out::println));
        server.setNumberOfThreads(8);
        server.start();
        server.stop();
        server.start();
        server.stop();
    }
}
