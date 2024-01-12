package com.teragrep.rlp_03.context;

public class InterestOpsStub implements InterestOps {

    InterestOpsStub() {

    }
    @Override
    public void add(int op) {
        throw new IllegalArgumentException("InterestOpsStub does not add");
    }

    @Override
    public void remove(int op) {
        throw new IllegalArgumentException("InterestOpsStub does not remove");
    }

    @Override
    public void removeAll() {
        throw new IllegalArgumentException("InterestOpsStub does not removeAll");
    }
}
