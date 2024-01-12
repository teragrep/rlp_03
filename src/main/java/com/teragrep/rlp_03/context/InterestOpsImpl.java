package com.teragrep.rlp_03.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;

public final class InterestOpsImpl implements InterestOps {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterestOpsImpl.class);

    private final SelectionKey selectionKey;

    private int currentOps;

    public InterestOpsImpl(SelectionKey selectionKey, int initialOps) {
        this.currentOps = initialOps;
        this.selectionKey = selectionKey;
        this.currentOps = selectionKey.interestOps(); // TODO ?
    }

    @Override
    public void add(int op) {
        int keysOps = selectionKey.interestOps();
        int newOps = currentOps | op;
        int opsAfter = (newOps & ~selectionKey.channel().validOps());
        if (opsAfter != 0){
            LOGGER.error("incompatible ops!!! newOps <{}> while valid <{}>, opsAfter <{}>", newOps, selectionKey.channel().validOps(), opsAfter);
        }
        LOGGER.debug("Adding op <{}> to currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", op , currentOps, newOps, selectionKey.interestOps(), selectionKey.channel().validOps());
        currentOps = newOps;
        try {
            selectionKey.interestOps(newOps);
        }
        catch (Throwable t) {
            LOGGER.error("add got throwable", t);
        }
        selectionKey.selector().wakeup();
        LOGGER.debug("Added op <{}>, currentOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, keysOps, selectionKey.channel().validOps());
    }

    @Override
    public void remove(int op) {
        int newOps = currentOps & ~op;
        LOGGER.debug("Removing op <{}> from currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", op , currentOps, newOps, selectionKey.interestOps(), selectionKey.channel().validOps());
        currentOps = newOps;
        try {
            selectionKey.interestOps(newOps);
        }
        catch (Throwable t) {
            LOGGER.error("remove got throwable", t);
        }
        selectionKey.selector().wakeup();
        LOGGER.debug("Removed op <{}>, currentOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, selectionKey.interestOps(), selectionKey.channel().validOps());
    }

    @Override
    public void removeAll() {
        int keysOps = selectionKey.interestOps();
        int newOps = 0;
        LOGGER.debug("Removing all currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", currentOps, newOps, keysOps, selectionKey.channel().validOps());
        try {
            selectionKey.interestOps(newOps);
        }
        catch (Throwable t) {
            LOGGER.error("removeAll got throwable", t);
        }
        selectionKey.selector().wakeup();
        LOGGER.debug("Removed all ops. currentOps <{}>, keyOps <{}>, validOps <{}>", currentOps, keysOps, selectionKey.channel().validOps());
    }
}
