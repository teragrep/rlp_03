package com.teragrep.rlp_03;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

class SyslogFrameProcessorImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyslogRXFrameProcessor.class);

    static Deque<RelpFrameTX> process(Deque<RelpFrameServerRX> rxDeque
            , Consumer<RelpFrameServerRX> cbFunction) {
        Deque<RelpFrameTX> txDeque = new ArrayDeque<>();

        for (RelpFrameServerRX rxFrame: rxDeque) {
            RelpFrameTX txFrame;
            switch (rxFrame.getCommand()) {
                case RelpCommand.ABORT:
                    // abort sends always serverclose
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txDeque.addLast(txFrame);
                    break;

                case RelpCommand.CLOSE:
                    // close is responded with rsp
                    txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "");
                    txDeque.addLast(txFrame);

                    // closure is immediate!
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txDeque.addLast(txFrame);

                    break;

                case RelpCommand.OPEN:
                    String responseData = "200 OK\nrelp_version=0\n"
                            + "relp_software=RLP-01,1.0.1,https://teragrep.com\n"
                            + "commands=" + RelpCommand.SYSLOG + "\n";
                    txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, responseData);
                    txDeque.addLast(txFrame);
                    break;

                case RelpCommand.RESPONSE:
                    // client must not respond
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txDeque.addLast(txFrame);
                    break;

                case RelpCommand.SERVER_CLOSE:
                    // client must not send serverclose
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txDeque.addLast(txFrame);
                    break;

                case RelpCommand.SYSLOG:
                    if (rxFrame.getData() != null) {
                        try {
                            cbFunction.accept(rxFrame);
                            txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "200 OK");
                        }
                        catch (Exception e) {
                            LOGGER.error("EXCEPTION WHILE PROCESSING " +
                                    "SYSLOG PAYLOAD: " + e);
                            txFrame = createResponse(rxFrame,
                                    RelpCommand.RESPONSE, "500 EXCEPTION " +
                                            "WHILE PROCESSING SYSLOG PAYLOAD");
                        }
                        txDeque.add(txFrame);
                    }
                    else {
                        txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "500 NO PAYLOAD");
                        txDeque.addLast(txFrame);
                    }
                    break;

                default:
                    break;

            }
        }

        return txDeque;
    }

    private static RelpFrameTX createResponse(
            RelpFrameServerRX rxFrame,
            String command,
            String response) {
        RelpFrameTX txFrame = new RelpFrameTX(command, response.getBytes(StandardCharsets.UTF_8));
        txFrame.setTransactionNumber(rxFrame.getTransactionNumber());
        return txFrame;
    }
}
