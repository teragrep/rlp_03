package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.SelectorNotification;

import java.nio.channels.SelectionKey;

public final class InterestOps {
    private final SelectionKey selectionKey;
    private final SelectorNotification selectorNotification;

    public InterestOps(SelectionKey selectionKey, SelectorNotification selectorNotification) {
        this.selectionKey = selectionKey;
        this.selectorNotification = selectorNotification;
    }

    void add(int op) {
        int currentOps = selectionKey.interestOps();
        int newOps = currentOps | op;
        selectionKey.interestOps(newOps);

        selectorNotification.wake();
    }

    void remove(int op) {
        int currentOps = selectionKey.interestOps();
        int newOps = currentOps & ~op;
        selectionKey.interestOps(newOps);

        selectorNotification.wake();
    }

    void removeAll() {
        selectionKey.interestOps(0);
        selectorNotification.wake();
    }
}
