package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.Selector;

public class SelectorNotification {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorNotification.class);

    private final Selector selector;

    SelectorNotification(Selector selector) {
        this.selector = selector;
    }

    public void wake() {
        LOGGER.info("waking selector");
        selector.wakeup();
    }
}
