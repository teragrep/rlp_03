package com.teragrep.rlp_03;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ManualTest {
    @Test // for testing with manual tools
    @EnabledIfSystemProperty(named="runServerTest", matches="true")
    public void runServerTest() throws IOException, InterruptedException {
        final Consumer<byte[]> cbFunction;
        AtomicLong asd = new AtomicLong();

        cbFunction = (message) -> {
            asd.getAndIncrement();
        };
        int port = 1601;
        Server server = new Server(port, new SyslogFrameProcessor(cbFunction));
        server.setNumberOfThreads(4);
        server.start();
        Thread.sleep(Long.MAX_VALUE);
    }
}
