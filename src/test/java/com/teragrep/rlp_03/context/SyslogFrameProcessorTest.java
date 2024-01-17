package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.SyslogFrameProcessor;
import com.teragrep.rlp_03.context.RelpFrameServerRX;
import com.teragrep.rlp_03.context.channel.SocketFake;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class SyslogFrameProcessorTest {

    private RelpFrameServerRX createOpenFrame (ConnectionContext connectionContext) {
        String requestData = "relp_version=0\n"
                + "relp_software=RLP-01,1.0.1,https://teragrep.com\n"
                + "commands=" + RelpCommand.SYSLOG;

        int requestDataLength =
                requestData.getBytes(StandardCharsets.UTF_8).length;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requestDataLength);

        byteBuffer.put(requestData.getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip(); // syslog frameprocessor assumes buffers in read mode

        return new RelpFrameServerRX(0, RelpCommand.OPEN,
                requestDataLength, byteBuffer, connectionContext);
    }

    private RelpFrameServerRX createSyslogFrame(ConnectionContext connectionContext, String requestData) {
        int requestDataLength =
                requestData.getBytes(StandardCharsets.UTF_8).length;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requestDataLength);

        byteBuffer.put(requestData.getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip(); // syslog frameprocessor assumes buffers in read mode

        return new RelpFrameServerRX(0, RelpCommand.SYSLOG,
                requestDataLength, byteBuffer, connectionContext);
    }

    private RelpFrameServerRX createCloseFrame(ConnectionContext connectionContext) {
        String requestData = "";
        int requestDataLength =
                requestData.getBytes(StandardCharsets.UTF_8).length;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(requestDataLength);

        byteBuffer.put(requestData.getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip(); // syslog frameprocessor assumes buffers in read mode

        return new RelpFrameServerRX(0, RelpCommand.CLOSE,
                requestDataLength, byteBuffer, connectionContext);
    }

    @Test
    public void testOpen() {
        List<byte[]> messageList = new LinkedList<>();

        Consumer<RelpFrameServerRX> testConsumer = (frame) -> messageList.add(frame.getData());

        SyslogFrameProcessor frameProcessor = new SyslogFrameProcessor(testConsumer);

        InterestOpsFake interestOpsFake = new InterestOpsFake();
        SocketFake socketFake = new SocketFake();
        RelpWriteFake relpWriteFake = new RelpWriteFake();
        ConnectionContext connectionContext = new ConnectionContextFake(interestOpsFake, socketFake, relpWriteFake);

        frameProcessor.process(createOpenFrame(connectionContext));

        RelpFrameTX txFrame = relpWriteFake.writtenFrames().get(0);

        Assertions.assertEquals(RelpCommand.RESPONSE, txFrame.getCommand());
        Assertions.assertEquals(0, messageList.size());
    }

    @Test
    public void testSyslogCommand() {
        List<byte[]> messageList = new LinkedList<>();

        Consumer<RelpFrameServerRX> testConsumer = (frame) -> messageList.add(frame.getData());

        SyslogFrameProcessor frameProcessor = new SyslogFrameProcessor(testConsumer);

        InterestOpsFake interestOpsFake = new InterestOpsFake();
        SocketFake socketFake = new SocketFake();
        RelpWriteFake relpWriteFake = new RelpWriteFake();
        ConnectionContext connectionContext = new ConnectionContextFake(interestOpsFake, socketFake, relpWriteFake);

        frameProcessor.process(createSyslogFrame(connectionContext, "test message"));

        RelpFrameTX txFrame = relpWriteFake.writtenFrames().get(0);

        Assertions.assertEquals(RelpCommand.RESPONSE, txFrame.getCommand());

        Assertions.assertEquals(
                "test message",
                new String(messageList.get(0), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void testOpenSyslogCloseSequence() {
        List<byte[]> messageList = new LinkedList<>();

        Consumer<RelpFrameServerRX> testConsumer = (frame) -> messageList.add(frame.getData());

        SyslogFrameProcessor frameProcessor = new SyslogFrameProcessor(testConsumer);

        InterestOpsFake interestOpsFake = new InterestOpsFake();
        SocketFake socketFake = new SocketFake();
        RelpWriteFake relpWriteFake = new RelpWriteFake();
        ConnectionContext connectionContext = new ConnectionContextFake(interestOpsFake, socketFake, relpWriteFake);

        frameProcessor.process(createOpenFrame(connectionContext));

        RelpFrameTX txFrameOpenResponse = relpWriteFake.writtenFrames().get(0);
        Assertions.assertEquals(RelpCommand.RESPONSE, txFrameOpenResponse.getCommand());

        frameProcessor.process(createSyslogFrame(connectionContext, "test message"));

        RelpFrameTX txFrameSyslogResponse = relpWriteFake.writtenFrames().get(1);
        Assertions.assertEquals(RelpCommand.RESPONSE, txFrameSyslogResponse.getCommand());

        frameProcessor.process(createCloseFrame(connectionContext));

        RelpFrameTX txFrameCloseResponse = relpWriteFake.writtenFrames().get(2);;
        Assertions.assertEquals(RelpCommand.RESPONSE, txFrameCloseResponse.getCommand());

        RelpFrameTX txFrameServerCloseResponse = relpWriteFake.writtenFrames().get(3);

        Assertions.assertEquals(RelpCommand.SERVER_CLOSE, txFrameServerCloseResponse.getCommand());


        Assertions.assertEquals(
                "test message",
                new String(messageList.get(0), StandardCharsets.UTF_8)
        );
    }
}
