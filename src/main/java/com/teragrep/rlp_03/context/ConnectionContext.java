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

package com.teragrep.rlp_03.context;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_01.TxID;
import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.context.channel.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * A per connection object that handles reading and writing messages from and to
 * the SocketChannel.
 */
public class ConnectionContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionContext.class);

    final Socket socket;

    final TxID txID;

    final ByteBuffer readBuffer;

    final InterestOps interestOps;

    private final ConcurrentLinkedQueue<RelpFrameTX> txDeque = new ConcurrentLinkedQueue<>();

    private enum RelpState {
        NONE,
        READ,
        WRITE
    }

    private RelpState relpState = RelpState.NONE;

    public ConnectionContext(InterestOps interestOps, ExecutorService executorService, Socket socket, FrameProcessor frameProcessor) {
        this.interestOps = interestOps;

        this.socket = socket;

        this.readBuffer = ByteBuffer.allocateDirect(512);

        this.txID = new TxID();
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
        LOGGER.trace( "relpServerTlsSocket.read> entry ");

        int totalBytesRead = socket.read(activeBuffer);

        LOGGER.trace( "relpServerTlsSocket.read> exit with totalBytesRead <{}>", totalBytesRead);

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
        LOGGER.trace( "relpServerTlsSocket.write> entry ");

        int totalBytesWritten = socket.write(responseBuffer);

        LOGGER.trace( "relpServerTlsSocket.write> exit with totalBytesWritten <{}>", totalBytesWritten);

        return totalBytesWritten;

    }

    public void close() throws Exception {
        LOGGER.info("closing");
        //messageReader.close();
    }


    public void handleEvent(SelectionKey selectionKey, SelectorNotification selectorNotification) throws IOException {



/*
        try {
            // call close on socket so frameProcessor can cleanup
            close();
        } catch (Exception e) {
            LOGGER.trace("clientRelpSocket.close(); threw", e);
        }
        selectionKey.attach(null);
        selectionKey.channel().close();
        selectionKey.cancel();
*/


        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        int amount = Integer.MAX_VALUE;
        while (amount > 0) {
            amount = socket.read(byteBuffer);
            System.out.println("amount: " + amount);
        }

        selectionKey.interestOps(0); // FIXME?

        if (amount == -1) {
            selectionKey.cancel();
        }

        //interestOps.removeAll();

    }
}
