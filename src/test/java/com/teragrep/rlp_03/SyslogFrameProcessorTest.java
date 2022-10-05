package com.teragrep.rlp_03;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameRX;
import com.teragrep.rlp_01.RelpFrameTX;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class SyslogFrameProcessorTest {

    @Test
    public void testOpen() {
        List<byte[]> messageList = new LinkedList<>();

        Consumer<byte[]> testConsumer = messageList::add;

        SyslogFrameProcessor frameProcessor = new SyslogFrameProcessor(testConsumer);

        Deque<RelpFrameRX> rxDeque = new ArrayDeque<>();

        String requestData = "relp_version=0\n"
                + "relp_software=RLP-01,1.0.1,https://teragrep.com\n"
                + "commands=" + RelpCommand.SYSLOG;

        int requestDataLength =
                requestData.getBytes(StandardCharsets.UTF_8).length;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requestDataLength);

        byteBuffer.put(requestData.getBytes(StandardCharsets.UTF_8));

        RelpFrameRX relpFrameRX = new RelpFrameRX(0, "open",
                requestDataLength, byteBuffer);

        rxDeque.addLast(relpFrameRX);

        Deque<RelpFrameTX> txDeque = frameProcessor.process(rxDeque);

        RelpFrameTX txFrame = txDeque.getFirst();
        Assertions.assertEquals(RelpCommand.RESPONSE, txFrame.getCommand());
    }
}
