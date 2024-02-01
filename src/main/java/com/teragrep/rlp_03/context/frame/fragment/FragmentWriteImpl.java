package com.teragrep.rlp_03.context.frame.fragment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.LinkedList;

public class FragmentWriteImpl implements FragmentWrite {

    private final LinkedList<ByteBuffer> bufferSliceList;

    FragmentWriteImpl(LinkedList<ByteBuffer> bufferSliceList) {
        this.bufferSliceList = bufferSliceList;
    }

    @Override
    public long write(GatheringByteChannel gbc) throws IOException {
        ByteBuffer[] buffers = bufferSliceList.toArray(new ByteBuffer[0]);
        return gbc.write(buffers);
    }

    @Override
    public boolean hasRemaining() {
        // TODO perhaps remove the ones that have none remaining and check for empty list
        boolean rv = false;
        for (ByteBuffer buffer : bufferSliceList) {
            if (buffer.hasRemaining()) {
                rv = true;
                break;
            }
        }
        return rv;
    }

    @Override
    public long length() {
        long length = 0;

        for (ByteBuffer buffer : bufferSliceList) {
            length = length + buffer.limit();
        }

        return length;
    }
}
