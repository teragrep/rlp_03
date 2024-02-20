package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.rental.Rental;
import com.teragrep.rlp_03.context.frame.rental.Lease;

// TODO tests
public class FragmentByteStreamRental implements FragmentByteStream {

    private final FragmentByteStream fragmentByteStream;
    private final Rental rental;


    FragmentByteStreamRental(FragmentByteStream fragmentByteStream, Rental rental) {
        this.fragmentByteStream = fragmentByteStream;
        this.rental = rental;
    }

    @Override
    public Byte get() {
        try (Lease ignored = rental.get()) {
            return fragmentByteStream.get();
        }
    }

    @Override
    public boolean next() {
        try (Lease ignored = rental.get()) {
            return fragmentByteStream.next();
        }
    }
}
