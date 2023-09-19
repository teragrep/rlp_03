package com.teragrep.rlp_03;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;

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
    @DisabledOnJre(JRE.JAVA_8)
    public void testServerShutdownMultiThread() throws IOException, InterruptedException {
        Server server = new Server(10601, new SyslogFrameProcessor(System.out::println));
        server.setNumberOfThreads(8);
        server.start();
        server.stop();
        server.start();
        server.stop();
    }
}
