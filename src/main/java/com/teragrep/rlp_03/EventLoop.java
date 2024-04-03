/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
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

import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

public class EventLoop implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventLoop.class);

    private final Selector selector;

    public EventLoop(Selector selector) {
        this.selector = selector;
    }

    public Selector selector() {
        return selector;
    }

    public void poll() throws IOException {
        int readyKeys = selector.select();

        LOGGER.debug("readyKeys <{}>", readyKeys);

        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        LOGGER.debug("selectionKeys <{}> ", selectionKeys);
        for (SelectionKey selectionKey : selectionKeys) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER
                        .debug(
                                "selectionKey <{}>: isValid <{}>, isConnectable <{}>, isAcceptable <{}>, isReadable <{}>, isWritable <{}>",
                                selectionKey, selectionKey.isValid(), selectionKey.isConnectable(),
                                selectionKey.isAcceptable(), selectionKey.isReadable(), selectionKey.isWritable()
                        );
            }

            if (selectionKey.isAcceptable()) {
                // ListenContext
                ListenContext listenContext = (ListenContext) selectionKey.attachment();
                listenContext.handleEvent(selectionKey);
            }
            else if (selectionKey.isConnectable()) {
                if (selectionKey.isConnectable()) {
                    ConnectContext connectContext = (ConnectContext) selectionKey.attachment();
                    connectContext.handleEvent(selectionKey);
                }
            }
            else {
                // ConnectionContext (aka EstablishedContext)
                ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                try {
                    connectionContext.handleEvent(selectionKey);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("SocketPoll.poll CancelledKeyException caught: {}", cke.getMessage());
                    connectionContext.close();
                }
            }
        }
        selectionKeys.clear();
    }

    @Override
    public void close() {
        for (SelectionKey selectionKey : selector.keys()) {
            Context context = ((Context) selectionKey.attachment());
            context.close();
        }
        try {
            selector.close();
        }
        catch (IOException ioException) {
            LOGGER.warn("selector close threw", ioException);
        }
    }

    public void wakeup() {
        selector.wakeup();
    }
}
