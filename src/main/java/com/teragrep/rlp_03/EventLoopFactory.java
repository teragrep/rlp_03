package com.teragrep.rlp_03;

import java.io.IOException;
import java.nio.channels.Selector;

public class EventLoopFactory {

    public EventLoop create() throws IOException {
        Selector selector = Selector.open();
        return new EventLoop(selector);
    }
}
