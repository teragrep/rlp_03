package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.access.Lease;

// TODO tests
public class FragmentByteStreamAccess implements FragmentByteStream {

    private final FragmentByteStream fragmentByteStream;
    private final Access access;


    FragmentByteStreamAccess(FragmentByteStream fragmentByteStream, Access access) {
        this.fragmentByteStream = fragmentByteStream;
        this.access = access;
    }

    @Override
    public Byte get() {
        try (Lease ignored = access.get()) {
            return fragmentByteStream.get();
        }
    }

    @Override
    public boolean next() {
        try (Lease ignored = access.get()) {
            return fragmentByteStream.next();
        }
    }
}
