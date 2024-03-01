package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.rental.Rental;
import com.teragrep.rlp_03.context.frame.rental.Lease;

import java.nio.ByteBuffer;

public class FragmentAccess implements Fragment {

    private final Fragment fragment;
    private final Rental rental;

    public FragmentAccess(Fragment fragment, Rental rental) {
        this.fragment = fragment;
        this.rental = rental;
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
        try (Lease ignored = rental.get()) {
            return fragment.toBytes();
        }
    }

    @Override
    public String toString() {
        try (Lease ignored = rental.get()) {
            return fragment.toString();
        }
    }

        @Override
    public int toInt() {
        try (Lease ignored = rental.get()) {
            return fragment.toInt();
        }
    }

    @Override
    public FragmentWrite toFragmentWrite() {
        return new FragmentWriteAccess(fragment.toFragmentWrite(), rental);
    }

    @Override
    public FragmentByteStream toFragmentByteStream() {
        return null;
    }

    @Override
    public long size() {
        try (Lease ignored = rental.get()) {
            return fragment.size();
        }
    }
}
