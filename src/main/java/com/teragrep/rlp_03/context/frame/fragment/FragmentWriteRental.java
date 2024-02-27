package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.access.Lease;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;

public class FragmentWriteRental implements FragmentWrite {
    private final FragmentWrite fragmentWrite;
    private final Access access;

    FragmentWriteRental(FragmentWrite fragmentWrite, Access access) {
        this.fragmentWrite = fragmentWrite;
        this.access = access;
    }

    @Override
    public long write(GatheringByteChannel gbc) throws IOException {
        try (Lease ignored = access.get()) {
            return fragmentWrite.write(gbc);
        }
    }

    @Override
    public boolean hasRemaining() {
        try (Lease ignored = access.get()) {
            return fragmentWrite.hasRemaining();
        }
    }

    @Override
    public long length() {
        try (Lease ignored = access.get()) {
            return fragmentWrite.length();
        }
    }
}
