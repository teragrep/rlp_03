package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.EncryptionInfo;
import com.teragrep.rlp_03.FrameProcessor;
import com.teragrep.rlp_03.StubEncryptionInfo;
import com.teragrep.rlp_03.TransportInfo;

import java.nio.channels.SocketChannel;
import java.util.function.Supplier;

public class PlainFactory extends ContextFactory {

    private final Supplier<FrameProcessor> frameProcessorSupplier;
    private final EncryptionInfo encryptionInfo;


    public PlainFactory(Supplier<FrameProcessor> frameProcessorSupplier) {
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.encryptionInfo = new StubEncryptionInfo();
    }

    @Override
    public ConnectionContext create(SocketChannel socketChannel) {
        TransportInfo transportInfo = new TransportInfo(socketChannel, encryptionInfo);

        return new PlainContext(
                        socketChannel,
                        frameProcessorSupplier.get(),
                        transportInfo
                );
    }
}
