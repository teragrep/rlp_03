package com.teragrep.rlp_03;

import java.nio.channels.Selector;

public class SelectorNotification {

    private final Selector selector;

    SelectorNotification(Selector selector) {
        this.selector = selector;
    }

    public void wake() {
        this.wake();
    }
}
