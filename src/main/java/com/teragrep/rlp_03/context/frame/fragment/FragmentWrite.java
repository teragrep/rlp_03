package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.access.SafeAccess;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.LinkedList;

public class FragmentWrite {

    private final LinkedList<ByteBuffer> bufferSliceList;
    private final SafeAccess safeAccess;

    // TODO extend safety lock here too

    FragmentWrite(LinkedList<ByteBuffer> bufferSliceList, SafeAccess safeAccess) {
        this.bufferSliceList = bufferSliceList;
        this.safeAccess = safeAccess;
    }

    public long write(GatheringByteChannel gbc) throws IOException {
        try (SafeAccess ignored = safeAccess.get()) {
            ByteBuffer[] buffers = bufferSliceList.toArray(new ByteBuffer[0]);
            return gbc.write(buffers);
        }
    }

    public boolean hasRemaining() {
        try (SafeAccess ignored = safeAccess.get()) {
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
    }

    public long length() {
        try (SafeAccess ignored = safeAccess.get()) {
            long length = 0;

            for (ByteBuffer buffer : bufferSliceList) {
                length = length + buffer.limit();
            }

            return length;
        }
    }
}
