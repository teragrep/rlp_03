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
import java.util.function.Function;

import com.teragrep.rlp_01.RelpFrameTX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tlschannel.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * A per connection object that handles reading and writing messages from and to
 * the SocketChannel.
 */
public class RelpServerTlsSocket extends RelpServerSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpServerTlsSocket.class);

    private long socketId;
    private final TlsChannel tlsChannel;

    private final TlsTransportInfo tlsTransportInfo;

    private final MessageReader messageReader;
    private final MessageWriter messageWriter;

    private final Deque<RelpFrameTX> txDeque = new ArrayDeque<>();

    private enum RelpState {
        NONE,
        READ,
        WRITE
    }

    private RelpState relpState = RelpState.NONE;


    /**
     * Constructor.
     *
     * @param socketChannel
     * The SocketChannel to read and write messages.
     * @param frameProcessor
     * The frame processor class containing list of requests and responses.
     */
    public RelpServerTlsSocket(SocketChannel socketChannel,
                               FrameProcessor frameProcessor,
                               SSLContext sslContext,
                               Function<SSLContext, SSLEngine> sslEngineFunction) {

        this.messageReader = new MessageReader(this, txDeque, frameProcessor);
        this.messageWriter = new MessageWriter(this, txDeque);

        this.tlsChannel = ServerTlsChannel
                .newBuilder(socketChannel, sslContext)
                .withEngineFactory(sslEngineFunction)
                .build();

        this.tlsTransportInfo = new TlsTransportInfo(socketChannel, tlsChannel);
    }

    /*
     * Tries to read incoming requests and changes state to WRITE if responses list
     * has been populated.
     */
    @Override
    public int processRead(int ops) {
        if (relpState == RelpState.WRITE) {
            return processWrite(ops);
        }

        ConnectionOperation cop;

        try {
            cop = messageReader.readRequest();
            relpState = RelpState.NONE;
        } catch (NeedsReadException e) {
            LOGGER.trace("r:" + e);
            relpState = RelpState.READ;
            return SelectionKey.OP_READ;

        } catch (NeedsWriteException e) {
            LOGGER.trace("r:" + e);
            relpState = RelpState.READ;
            return SelectionKey.OP_WRITE;

        } catch (IOException ioException) {
            cop = ConnectionOperation.CLOSE;
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
    @Override
    public int processWrite(int ops) {
        if (relpState == RelpState.READ) {
            return processRead(ops);
        }

        ConnectionOperation cop = ConnectionOperation.WRITE;

        if (txDeque.size() > 0) {
            try {
                cop = messageWriter.writeResponse();
                relpState = RelpState.NONE;
            } catch (NeedsReadException e) {
                LOGGER.trace("w:" + e);
                relpState = RelpState.WRITE;
                return SelectionKey.OP_READ;

            } catch (NeedsWriteException e) {
                LOGGER.trace("w:" + e);
                relpState = RelpState.WRITE;
                return SelectionKey.OP_WRITE;
            } catch (IOException ioException) {
                cop = ConnectionOperation.CLOSE;
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
    @Override
    int read(ByteBuffer activeBuffer) throws IOException {
        activeBuffer.clear();
        LOGGER.trace( "relpServerTlsSocket.read> entry ");

        int totalBytesRead = tlsChannel.read(activeBuffer);

        LOGGER.trace( "relpServerTlsSocket.read> exit with totalBytesRead: " + totalBytesRead);

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
    @Override
    int write(ByteBuffer responseBuffer) throws IOException {
        LOGGER.trace( "relpServerTlsSocket.write> entry ");

        int totalBytesWritten = tlsChannel.write(responseBuffer);

        LOGGER.trace( "relpServerTlsSocket.write> exit with totalBytesWritten: " + totalBytesWritten);

        return totalBytesWritten;

    }

    @Override
    public void setSocketId(long socketId) {
        this.socketId = socketId;
    }

    @Override
    public long getSocketId() {
        return socketId;
    }

    @Override
    TransportInfo getTransportInfo() {
        return tlsTransportInfo;
    }

    @Override
    public void close() throws Exception {
        messageReader.close();
    }
}
