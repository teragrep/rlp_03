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

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Deque;

// Response writer class that takes created responses from the RelpFrameTX list and writes it to the socket.
class MessageWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageWriter.class);
    private final RelpServerSocket relpServerSocket;
    private final Deque<RelpFrameTX> txDeque;
    private ByteBuffer responseBuffer = null;

    public MessageWriter(RelpServerSocket relpServerSocket,
                         Deque<RelpFrameTX> txDeque) {
        this.relpServerSocket = relpServerSocket;
        this.txDeque = txDeque;
    }


    /**
     * Takes a response frame from the list, writes the message into the responseBuffer and sends
     * the message to the RelpServerSocket to be written to the socketchannel.
     *
     * @return READ state if there are no responses to be written, WRITE state if there is, and CLOSE
     * state if there are exceptions or SERVER_CLOSE message has been sent.
     */
    ConnectionOperation writeResponse() throws  IOException{
        LOGGER.trace("messageWriter.writeResponse> entry ");

        if (responseBuffer == null) {

            RelpFrameTX frame = txDeque.removeFirst();

            if (frame != null) {

                LOGGER.trace("messageWriter.writeResponse> frame ");

                responseBuffer = ByteBuffer.allocateDirect(frame.length());


                frame.write(responseBuffer);

                responseBuffer.flip();
                LOGGER.trace("messageWriter.writeResponse> responseBuffer ");

                int bytesWritten = relpServerSocket.write(responseBuffer);

                if (bytesWritten == -1) {
                    return ConnectionOperation.CLOSE;
                }

                // serverclose is special, it does not care if data is completely written
                if (frame.getCommand().equals(RelpCommand.SERVER_CLOSE)) {
                    return ConnectionOperation.CLOSE;
                }
            }
        } else {
            try {
                int bytesWritten = relpServerSocket.write(responseBuffer);

                if (bytesWritten == -1) {
                    return ConnectionOperation.CLOSE;
                }
            }
            catch (IOException ioException) {
                return ConnectionOperation.CLOSE;
            }
        }
        if (responseBuffer != null && !responseBuffer.hasRemaining()) {
            responseBuffer = null;
        }
        LOGGER.trace("messageWriter.writeResponse> exit ");
        if (responseBuffer == null) {
            return ConnectionOperation.READ;
        }
        else {
            return ConnectionOperation.WRITE;
        }
    }
}