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
package com.teragrep.rlp_03.client;

import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.channel.context.ConnectContext;
import com.teragrep.rlp_03.channel.context.ConnectContextFactory;
import com.teragrep.rlp_03.channel.context.EstablishedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Factory for creating {@link Client}
 */
public class ClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientFactory.class);
    private final ConnectContextFactory connectContextFactory;
    private final EventLoop eventLoop;

    /**
     * Main for Constructor for {@link ClientFactory}
     * 
     * @param connectContextFactory {@link ConnectContextFactory} for creating new connections
     * @param eventLoop             {@link EventLoop} to register new connections with
     */
    public ClientFactory(ConnectContextFactory connectContextFactory, EventLoop eventLoop) {
        this.connectContextFactory = connectContextFactory;
        this.eventLoop = eventLoop;
    }

    /**
     * Opens up a new connection. Registers the connection to provided {@link EventLoop}. Note that the
     * {@link EventLoop} needs to run in order to proceed with the connection.
     * 
     * @param inetSocketAddress destination {@link InetSocketAddress} to connect to.
     * @return {@link Client} once connection succeeds.
     * @throws IOException          if connection fails
     * @throws InterruptedException if {@link Future<EstablishedContext>} is interrupted.
     * @throws ExecutionException   if {@link Future<EstablishedContext>} fails to complete successfully.
     */
    // TODO add timeout for the future so that connection attempt times out
    public Client open(InetSocketAddress inetSocketAddress)
            throws IOException, InterruptedException, ExecutionException {
        // this is for returning ready connection
        CompletableFuture<EstablishedContext> readyContextFuture = new CompletableFuture<>();
        Consumer<EstablishedContext> establishedContextConsumer = readyContextFuture::complete;

        ClientDelegate clientDelegate = new ClientDelegate();

        ConnectContext connectContext;
        try {
            connectContext = connectContextFactory
                    .create(inetSocketAddress, clientDelegate, establishedContextConsumer);
        }
        catch (IOException ioException) {
            clientDelegate.close();
            throw ioException;
        }
        LOGGER.debug("registering to eventLoop <{}>", eventLoop);
        eventLoop.register(connectContext);
        LOGGER.debug("registered to eventLoop <{}>", eventLoop);
        EstablishedContext establishedContext = readyContextFuture.get();
        LOGGER.debug("returning establishedContext <{}>", establishedContext);
        return clientDelegate.create(establishedContext);
    }
}
