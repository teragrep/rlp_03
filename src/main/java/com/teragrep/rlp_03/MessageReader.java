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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_01.RelpParser;
import com.teragrep.rlp_01.TxID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;

/*
 * Request reader class that reads incoming requests and sends them out for processing.
 */
class MessageReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReader.class);

    private final RelpServerSocket relpServerSocket;
    private final Deque<RelpFrameTX> txDeque;
    private final ByteBuffer readBuffer;
    private final FrameProcessor frameProcessor;
    private final TxID txIdChecker = new TxID();

    private RelpParser relpParser;

    /**
     * Constructor.
     */
    MessageReader(RelpServerSocket relpServerSocket, Deque<RelpFrameTX> txDeque,
                  FrameProcessor frameProcessor) {
        this.frameProcessor = frameProcessor;
        this.relpServerSocket = relpServerSocket;
        this.txDeque = txDeque;
        this.readBuffer = ByteBuffer.allocateDirect(MAX_HEADER_CAPACITY + 1024*256);
        this.relpParser = new RelpParser(false);
    }

    // Maximum capacity for HEADER part of RELP message frames:
    // (MAX TXNR = 999999999).length + SP.length + (MAX COMMAND = "serverclose").length + SP.length + DATALEN.length + NL.length
    private final int MAX_HEADER_CAPACITY = Integer.toString(txIdChecker.MAX_ID).length() +
            " ".length() +
            "serverclose".length() +
            " ".length() +
            Long.toString(Long.MAX_VALUE).length() +
            " ".length();


    /**
     * Reads incoming requests from the associated RelpServerSocket, parses each incoming
     * byte until there is a complete message, creates a frame for the parsed message, adds
     * it to the to be processed RelpFrameServerRX queue and calls on frameProcessor to process it.
     *
     * @return READ state.
     */
    ConnectionOperation readRequest() throws IOException {
        LOGGER.debug("messageReader.readRequest> entry with parser: " + relpParser + " and parser state: " + relpParser.getState());


        int readBytes = relpServerSocket.read(readBuffer);

        while (readBytes > 0) {
            readBuffer.flip(); // for reading
            while (readBuffer.hasRemaining()) {
                relpParser.parse(readBuffer.get());
                if (relpParser.isComplete()) {
                    LOGGER.debug("messageReader.readRequest> read entire message complete ");

                    // TODO read long as we can to process batches
                    Deque<RelpFrameServerRX> rxFrames = new ArrayDeque<>();
                    RelpFrameServerRX rxFrame = new RelpFrameServerRX(
                            relpParser.getTxnId(),
                            relpParser.getCommandString(),
                            relpParser.getLength(),
                            relpParser.getData(),
                            relpServerSocket.getTransportInfo()
                    );
                    rxFrames.addLast(rxFrame);
                    txDeque.addAll(frameProcessor.process(rxFrames));

                    // reset parser state, TODO improve performance by having clear
                    relpParser = new RelpParser(false);
                }
            }
            readBuffer.compact();
            readBuffer.flip(); // for writing
            try {
                // read until there is no more data available
                readBytes = relpServerSocket.read(readBuffer);
            }
            catch (NeedsReadException | NeedsWriteException tlsException) {
                break;
            }
        }
        if (readBytes < 0) {
            // problem with socket, closing
            LOGGER.debug("messageReader.readRequest> closing. " +
                    "readBytes: " + readBytes);
            return ConnectionOperation.CLOSE;
        }
        else {
            LOGGER.debug("messageReader.readRequest> exit with readBuffer: " + readBuffer);

            return ConnectionOperation.READ;
        }
    }
}

