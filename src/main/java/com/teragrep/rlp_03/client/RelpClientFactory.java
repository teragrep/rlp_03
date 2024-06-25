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

import com.teragrep.net_01.channel.context.ConnectContextFactory;
import com.teragrep.net_01.channel.context.EstablishedContext;
import com.teragrep.net_01.client.EstablishedContextFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public final class RelpClientFactory {

    private final ConnectContextFactory connectContextFactory;
    private final EventLoop eventLoop;

    /**
     * Main for Constructor for {@link RelpClientFactory}
     *
     * @param connectContextFactory {@link ConnectContextFactory} for creating new connections
     * @param eventLoop             {@link EventLoop} to register new connections with
     */
    public RelpClientFactory(ConnectContextFactory connectContextFactory, EventLoop eventLoop) {
        this.connectContextFactory = connectContextFactory;
        this.eventLoop = eventLoop;
    }

    /**
     * Opens up a new connection. Registers the connection to provided {@link EventLoop}. Note that the
     * {@link EventLoop} needs to run in order to proceed with the connection.
     *
     * @param inetSocketAddress destination {@link InetSocketAddress} to connect to.
     * @return a {@link RelpClient} {@link CompletableFuture}.
     */
    public CompletableFuture<RelpClient> open(InetSocketAddress inetSocketAddress) {

        // RelpClientDelegate will receive the server send frames, and it needs to be registered into the connection
        RelpClientDelegate relpClientDelegate = new RelpClientDelegate();
        FrameDelegationClockFactory frameDelegationClockFactory = new FrameDelegationClockFactory(
                () -> relpClientDelegate
        );

        EstablishedContextFactory establishedContextFactory = new EstablishedContextFactory(
                connectContextFactory,
                eventLoop,
                frameDelegationClockFactory
        );

        CompletableFuture<EstablishedContext> establishedContextCompletableFuture = establishedContextFactory
                .open(inetSocketAddress);

        BiFunction<EstablishedContext, Throwable, EstablishedContext> clientDelegateClosure = (
                establishedContext,
                throwable
        ) -> {
            if (throwable != null) {
                relpClientDelegate.close();
                throw new RuntimeException(throwable);
            }
            return establishedContext;
        };

        establishedContextCompletableFuture.handle(clientDelegateClosure);

        // RelpClientDelegate will return a RelpClient that assigns the transaction numbers and tracks their replies when transmitting frames
        return establishedContextCompletableFuture.thenApply(relpClientDelegate::create);
    }
}
