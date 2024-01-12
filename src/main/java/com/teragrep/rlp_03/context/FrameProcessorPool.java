package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.FrameProcessor;
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

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean close;

    FrameProcessorPool(final Supplier<FrameProcessor> frameProcessorSupplier) {
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.queue = new ConcurrentLinkedQueue<>();
        this.frameProcessorStub = new FrameProcessorStub();
        this.close = new AtomicBoolean();

        // TODO maximum number of available frameProcessors should be perhaps limited?
    }

    FrameProcessor take() {
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

    void offer(FrameProcessor frameProcessor) {
        if (!frameProcessor.isStub()) {
            queue.add(frameProcessor);
        }

        if (close.get()) {
            while (queue.peek() != null) {
                if (lock.tryLock()) {
                    while (true) {
                        FrameProcessor queuedFrameProcessor = queue.poll();
                        if (queuedFrameProcessor == null) {
                            break;
                        } else {
                            try {
                                LOGGER.debug("Closing frameProcessor <{}>", frameProcessor);
                                queuedFrameProcessor.close();
                                LOGGER.debug("Closed frameProcessor <{}>", frameProcessor);
                            } catch (Exception exception) {
                                LOGGER.warn("Exception <{}> while closing frameProcessor <{}>", exception.getMessage(), frameProcessor);
                            }
                        }
                    }
                    lock.unlock();
                } else {
                    break;
                }
            }
        }
    }

    void close() {
        close.set(true);

        // close all that are in the pool right now
        offer(frameProcessorStub);
    }
}
