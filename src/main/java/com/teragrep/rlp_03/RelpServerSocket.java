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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;


import com.teragrep.rlp_01.RelpFrameTX;

/**
 * A per connection object that handles reading and writing messages from and to
 * the SocketChannel.
 */
public class RelpServerSocket {

    private long socketId;
    private final SocketChannel socketChannel;

    private final MessageReader messageReader;
    private final MessageWriter messageWriter;
    public boolean endOfStreamReached = false;

    private final Deque<RelpFrameTX> txDeque = new ArrayDeque<>();

    /**
     * Constructor.
     *
     * @param socketChannel
     * The SocketChannel to read and write messages.
     * @param frameProcessor
     * The frame processor class containing list of requests and responses.
     */
    public RelpServerSocket(SocketChannel socketChannel, FrameProcessor frameProcessor) {
        this.socketChannel = socketChannel;
        this.messageReader = new MessageReader(this, txDeque, frameProcessor);
        this.messageWriter = new MessageWriter(this, txDeque);
    }

    /*
     * Tries to read incoming requests and changes state to WRITE if responses list
     * has been populated.
     */
    public int processRead(int ops) {
        ConnectionOperation cop = ConnectionOperation.READ;

        try {
            cop = messageReader.readRequest();
        } catch (Exception e) {
            // FIXME
            e.printStackTrace();
        }

        if (txDeque.size() > 0) {
            cop = ConnectionOperation.WRITE;
        }

        // if a message is ready, interested in writes
        if (cop == ConnectionOperation.CLOSE) {
            return 0;
        } else if (cop == ConnectionOperation.WRITE) {
            return ops | SelectionKey.OP_WRITE;
        } else {
            return ops;
        }
    }

    /*
     * Tries to write ready responses into the socket.
     */
    public int processWrite(int ops) {
        ConnectionOperation cop = ConnectionOperation.WRITE;

        if (txDeque.size() > 0) {
            try {
                cop = messageWriter.writeResponse();
            } catch (Exception e) {
                // FIXME
                e.printStackTrace();
            }
        }

        if (txDeque.size() > 0 && cop != ConnectionOperation.CLOSE) {
            cop = ConnectionOperation.WRITE;
        }

        if (cop == ConnectionOperation.CLOSE) {
            return 0;
        } else if (cop == ConnectionOperation.WRITE) {
            // if nothing more to write, not interested in writes
            return ops;
        } else {
            return ops ^ SelectionKey.OP_WRITE;
        }
    }

    /**
     * Reads incoming messages from the socketChannel into the given activeBuffer.
     *
     * @param activeBuffer
     * The ByteBuffer to read messages into.
     * @return total read bytes.
     */
    int read(ByteBuffer activeBuffer) throws IOException {
        activeBuffer.clear();
        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "relpServerSocket.read> entry ");
        }
        if (!socketChannel.isConnected() ) return -1;
        int bytesRead = socketChannel.read(activeBuffer);
        int totalBytesRead = bytesRead;

        while(bytesRead > 0){
            bytesRead = socketChannel.read(activeBuffer);
            totalBytesRead += bytesRead;
        }
        if(bytesRead == -1){
            endOfStreamReached = true;
        }

        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "relpServerSocket.read> exit with totalBytesRead: " + totalBytesRead);
        }

        return totalBytesRead;
    }

    /**
     * Writes the message in responseBuffer into the socketChannel.
     *
     * @param responseBuffer
     * The ByteBuffer containing the response frame.
     *
     * @return total bytes written.
     */
    int write(ByteBuffer responseBuffer) throws IOException {
        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "relpServerSocket.write> entry ");
        }
        if (!socketChannel.isConnected() ) return -1;
        int bytesWritten      = socketChannel.write(responseBuffer);
        int totalBytesWritten = bytesWritten;

        while(bytesWritten > 0 && responseBuffer.hasRemaining()){
            bytesWritten = socketChannel.write(responseBuffer);
            totalBytesWritten += bytesWritten;
        }

        if( System.getenv( "RELP_SERVER_DEBUG" ) != null ) {
            System.out.println( "relpServerSocket.write> exit with totalBytesWritten: " + totalBytesWritten);
        }

        return totalBytesWritten;

    }

    public void setSocketId(long socketId) {
        this.socketId = socketId;
    }

    public long getSocketId() {
        return socketId;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }


}
