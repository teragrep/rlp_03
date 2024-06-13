package com.teragrep.rlp_03.channel.context;

import com.teragrep.rlp_03.channel.buffer.BufferLease;
import com.teragrep.rlp_03.channel.buffer.BufferLeasePool;
import com.teragrep.rlp_03.frame.FrameClock;
import com.teragrep.rlp_03.frame.FrameClockLeaseful;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameAccess;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Clock {
    private static final Logger LOGGER = LoggerFactory.getLogger(Clock.class);

    private final EstablishedContext establishedContext;
    private final BufferLeasePool bufferLeasePool;
    private final FrameDelegate frameDelegate;

    private final FrameClockLeaseful frameClockLeaseful;

    Clock(BufferLeasePool bufferLeasePool, EstablishedContext establishedContext, FrameDelegate frameDelegate) {
        this.bufferLeasePool = bufferLeasePool;
        this.establishedContext = establishedContext;
        this.frameDelegate = frameDelegate;

        this.frameClockLeaseful = new FrameClockLeaseful(bufferLeasePool, new FrameClock());
    }

    boolean advance(BufferLease bufferLease) {
        //
        throw new UnsupportedOperationException("not implemented yet");
    }


    private boolean delegateFrame(RelpFrame relpFrame) {
        boolean rv;

        RelpFrameAccess relpFrameAccess = new RelpFrameAccess(relpFrame);
        FrameContext frameContext = new FrameContext(establishedContext, relpFrameAccess);

        rv = frameDelegate.accept(frameContext);

        LOGGER.debug("processed txFrame.");
        return rv;
    }

}
