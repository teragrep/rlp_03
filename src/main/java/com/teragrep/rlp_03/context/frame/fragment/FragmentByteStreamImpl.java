package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;
import java.util.LinkedList;


// TODO tests
class FragmentByteStreamImpl implements FragmentByteStream {

    private final LinkedList<ByteBuffer> bufferCopies;

    private ByteBuffer currentBuffer;

    private int limit;
    private int position;

    private static final ByteBuffer byteBufferStub = ByteBuffer.allocateDirect(0);


    public FragmentByteStreamImpl(LinkedList<ByteBuffer> bufferCopies) {
        this.bufferCopies = bufferCopies;
        this.limit = 0;
        this.position = -1;
        this.currentBuffer = byteBufferStub;
    }

    private boolean changeBuffer() {
        boolean rv = false;
        if (!bufferCopies.isEmpty()) {
            currentBuffer = bufferCopies.pop();
            limit = currentBuffer.limit();
            position = -1;
            rv = true;
        }
        return rv;
    }

    @Override
    public Byte get() {
        return currentBuffer.get(position);
    }

    @Override
    public boolean next() {
        boolean rv = false;
        if (position < limit - 1) {
            position++;
            rv = true;
        }
        else {
            while (changeBuffer()) {
                if (position < limit - 1) {
                    position++;
                    rv = true;
                    break;
                }
            }
        }
        return rv;
    }
}
