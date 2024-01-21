package com.teragrep.rlp_03.context.frame.fragment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.LinkedList;

public class FragmentWrite {

    LinkedList<ByteBuffer> bufferSliceList;

    // TODO extend safety lock here too

    FragmentWrite(LinkedList<ByteBuffer> bufferSliceList) {
        this.bufferSliceList = bufferSliceList;
    }

    public long write(GatheringByteChannel gbc) throws IOException {
        ByteBuffer[] buffers = bufferSliceList.toArray(new ByteBuffer[0]);
        return gbc.write(buffers);
    }

    public boolean hasRemaining() {
        boolean rv = false;
        for (ByteBuffer buffer : bufferSliceList) {
            if (buffer.hasRemaining()) {
                rv = true;
                break;
            }
        }
        return rv;
    }

    public long length() {
        long length = 0;

        for (ByteBuffer buffer : bufferSliceList) {
            length = length + buffer.limit();
        }

        return length;
    }
}
