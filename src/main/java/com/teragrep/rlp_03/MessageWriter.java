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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

// Response writer class that takes created responses from the RelpFrameTX list and writes it to the socket.
class MessageWriter {
    private final RelpServerSocket relpServerSocket;
    // TODO implement better
    private final LinkedList<RelpFrameTX> txList;
    private ByteBuffer responseBuffer = null;

    public MessageWriter(RelpServerSocket relpServerSocket, LinkedList<RelpFrameTX> txList) {
        this.relpServerSocket = relpServerSocket;
        this.txList = txList;
    }


    /**
     * Takes a response frame from the list, writes the message into the responseBuffer and sends
     * the message to the RelpServerSocket to be written to the socketchannel.
     *
     * @return READ state if there are no responses to be written, WRITE state if there is, and CLOSE
     * state if there are exceptions or SERVER_CLOSE message has been sent.
     */
    ConnectionOperation writeResponse(){
        if (System.getenv("RELP_SERVER_DEBUG") != null) {
            System.out.println("messageWriter.writeResponse> entry ");
        }

        if (responseBuffer == null) {

            RelpFrameTX frame = txList.pop();

            if (frame != null) {

                if (System.getenv("RELP_SERVER_DEBUG") != null) {
                    System.out.println("messageWriter.writeResponse> frame ");
                }

                try {
                    responseBuffer = ByteBuffer.allocateDirect(frame.length());
                } catch (UnsupportedEncodingException e) {
                    // TODO
                    e.printStackTrace();
                }
                try {
                    frame.write(responseBuffer);
                } catch (IOException e) {
                    // TODO
                    e.printStackTrace();
                }
                responseBuffer.flip();
                if (System.getenv("RELP_SERVER_DEBUG") != null) {
                    System.out.println("messageWriter.writeResponse> responseBuffer ");
                }
                try {
                    int bytesWritten = relpServerSocket.write(responseBuffer);

                    if (bytesWritten == -1) {
                        return ConnectionOperation.CLOSE;
                    }
                }
                catch (IOException ioException) {
                    //ioException.printStackTrace();
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
                //ioException.printStackTrace();
                return ConnectionOperation.CLOSE;
            }
        }
        if (responseBuffer != null && !responseBuffer.hasRemaining()) {
            responseBuffer = null;
        }
        if (System.getenv("RELP_SERVER_DEBUG") != null) {
            System.out.println("messageWriter.writeResponse> exit ");
        }
        if (responseBuffer == null) {
            return ConnectionOperation.READ;
        }
        else {
            return ConnectionOperation.WRITE;
        }
    }
}