package com.teragrep.rlp_03;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FrameProcessorPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrameProcessorPool.class);

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final ConcurrentLinkedQueue<FrameProcessor> queue;

    private final FrameProcessor frameProcessorStub;

    private final AtomicBoolean closeInProgress;

    private final AtomicBoolean close;

    public FrameProcessorPool(final Supplier<FrameProcessor> frameProcessorSupplier) {
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.queue = new ConcurrentLinkedQueue<>();
        this.frameProcessorStub = new FrameProcessorStub();
        this.closeInProgress = new AtomicBoolean();
        this.close = new AtomicBoolean();

        // TODO maximum number of available frameProcessors should be perhaps limited?
    }

    public FrameProcessor take() {
        FrameProcessor frameProcessor;
        if (close.get()) {
            frameProcessor = frameProcessorStub;
        }
        else {
            // get or create
            frameProcessor = queue.poll();
            if (frameProcessor == null) {
                frameProcessor = frameProcessorSupplier.get();
            }
        }

        return frameProcessor;
    }

    public void offer(FrameProcessor frameProcessor) {
        if (!frameProcessor.isStub()) {
            queue.add(frameProcessor);
        }

        if (close.get()) {
            while (queue.peek() != null) {
                if (closeInProgress.compareAndSet(false, true)) {
                    while (true) {
                        FrameProcessor queuedFrameProcessor = queue.poll();
                        if (queuedFrameProcessor == null) {
                            break;
                        } else {
                            try {
                                LOGGER.debug("Closing frameProcessor <{}>", queuedFrameProcessor);
                                queuedFrameProcessor.close();
                                LOGGER.debug("Closed frameProcessor <{}>", queuedFrameProcessor);
                            } catch (Exception exception) {
                                LOGGER.warn("Exception <{}> while closing frameProcessor <{}>", exception.getMessage(), queuedFrameProcessor);
                            }
                        }
                    }
                    if (!closeInProgress.compareAndSet(true, false)) {
                        throw new IllegalStateException("logic failure");
                    }
                } else {
                    break;
                }
            }
        }
    }

    public void close() {
        close.set(true);

        // close all that are in the pool right now
        offer(frameProcessorStub);
    }
}
