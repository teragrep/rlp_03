/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021  Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */

package com.teragrep.rlp_03;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameRX;
import com.teragrep.rlp_01.RelpFrameTX;

/**
 * Implements the process() method for the FrameProcessor. Takes each request from
 * the rxFrameList, creates a response frame for it and adds it to the txFrameList.
 */
public class SyslogFrameProcessor implements FrameProcessor {

    private final Consumer<byte[]> cbFunction;

    public SyslogFrameProcessor(Consumer<byte[]> cbFunction) {
        this.cbFunction = cbFunction;
    }

    @Override
    public List<RelpFrameTX> process(List<RelpFrameRX> rxFrameList) {
        LinkedList<RelpFrameTX> txFrameList = new LinkedList<>();

        for (RelpFrameRX rxFrame: rxFrameList) {
            RelpFrameTX txFrame;
            switch (rxFrame.getCommand()) {
                case RelpCommand.ABORT:
                    // abort sends always serverclose
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txFrameList.add(txFrame);
                    break;

                case RelpCommand.CLOSE:
                    // close is responded with rsp
                    txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "");
                    txFrameList.add(txFrame);

                    // closure is immediate!
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txFrameList.add(txFrame);

                    break;

                case RelpCommand.OPEN:
                    String responseData = "200 OK\nrelp_version=0\n"
                            + "relp_software=RLP-01,1.0.1,https://teragrep.com\n"
                            + "commands=" + RelpCommand.SYSLOG + "\n";
                    txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, responseData);
                    txFrameList.add(txFrame);
                    break;

                case RelpCommand.RESPONSE:
                    // client must not respond
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txFrameList.add(txFrame);
                    break;

                case RelpCommand.SERVER_CLOSE:
                    // client must not send serverclose
                    txFrame = createResponse(rxFrame, RelpCommand.SERVER_CLOSE, "");
                    txFrameList.add(txFrame);
                    break;

                case RelpCommand.SYSLOG:
                    if (rxFrame.getData() != null) {
                        cbFunction.accept(rxFrame.getData());
                        txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "200 OK");
                        txFrameList.add(txFrame);
                    }
                    else {
                        txFrame = createResponse(rxFrame, RelpCommand.RESPONSE, "500 NO PAYLOAD");
                        txFrameList.add(txFrame);
                    }
                    break;

                default:
                    break;

            }
        }


        return txFrameList;
    }

    private RelpFrameTX createResponse(
            RelpFrameRX rxFrame,
            String command,
            String response) {
        try {
            RelpFrameTX txFrame = new RelpFrameTX(command, response.getBytes("UTF-8"));
            txFrame.setTransactionNumber(rxFrame.getTransactionNumber());
            return txFrame;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(); // FIXME
        }
        return null;
    }
}

