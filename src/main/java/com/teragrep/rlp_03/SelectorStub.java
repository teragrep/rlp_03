package com.teragrep.rlp_03;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class SelectorStub extends Selector {

    SelectorStub() {
        super();
    }
    @Override
    public boolean isOpen() {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public SelectorProvider provider() {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public Set<SelectionKey> keys() {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public int selectNow() throws IOException {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public int select(long l) throws IOException {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public int select() throws IOException {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public Selector wakeup() {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }

    @Override
    public void close() throws IOException {
        throw new IllegalArgumentException("SelectorStub does not implement this.");
    }
}
