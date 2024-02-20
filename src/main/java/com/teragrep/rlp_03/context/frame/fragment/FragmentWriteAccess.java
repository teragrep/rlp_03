package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.access.Rental;
import com.teragrep.rlp_03.context.frame.access.Lease;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;

public class FragmentWriteAccess implements FragmentWrite {
    private final FragmentWrite fragmentWrite;
    private final Rental rental;

    FragmentWriteAccess(FragmentWrite fragmentWrite, Rental rental) {
        this.fragmentWrite = fragmentWrite;
        this.rental = rental;
    }

    @Override
    public long write(GatheringByteChannel gbc) throws IOException {
        try (Lease ignored = rental.get()) {
            return fragmentWrite.write(gbc);
        }
    }

    @Override
    public boolean hasRemaining() {
        try (Lease ignored = rental.get()) {
            return fragmentWrite.hasRemaining();
        }
    }

    @Override
    public long length() {
        try (Lease ignored = rental.get()) {
            return fragmentWrite.length();
        }
    }
}
