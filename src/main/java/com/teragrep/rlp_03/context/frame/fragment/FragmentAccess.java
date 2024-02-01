package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.access.Access;
import com.teragrep.rlp_03.context.frame.access.Lease;

import java.nio.ByteBuffer;

public class FragmentAccess implements Fragment {

    private final Fragment fragment;
    private final Access access;

    public FragmentAccess(Fragment fragment, Access access) {
        this.fragment = fragment;
        this.access = access;
    }

    @Override
    public void accept(ByteBuffer input) {
        fragment.accept(input);
    }

    @Override
    public boolean isStub() {
        return fragment.isStub();
    }

    @Override
    public boolean isComplete() {
        return fragment.isComplete();
    }

    @Override
    public byte[] toBytes() {
        try (Lease ignored = access.get()) {
            return fragment.toBytes();
        }
    }

    @Override
    public String toString() {
        try (Lease ignored = access.get()) {
            return fragment.toString();
        }
    }

        @Override
    public int toInt() {
        try (Lease ignored = access.get()) {
            return fragment.toInt();
        }
    }

    @Override
    public FragmentWrite toFragmentWrite() {
        return new FragmentWriteAccess(fragment.toFragmentWrite(), access);
    }
}
