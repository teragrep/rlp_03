package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpFrameTX;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RelpWriteFake implements RelpWrite {

    private final List<RelpFrameTX> writtenFrames;

    private final AtomicBoolean needRead;
    RelpWriteFake() {
        this.writtenFrames = new LinkedList<>();
        this.needRead = new AtomicBoolean();
    }
    @Override
    public void accept(List<RelpFrameTX> relpFrameTXList) {
        writtenFrames.addAll(relpFrameTXList);
    }

    @Override
    public void run() {
        // no-op
    }

    @Override
    public AtomicBoolean needRead() {
        return needRead;
    }

    // for testing
    List<RelpFrameTX> writtenFrames() {
        return writtenFrames;
    }
}
