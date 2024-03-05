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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;

public final class InterestOpsImpl implements InterestOps {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterestOpsImpl.class);

    private final SelectionKey selectionKey;

    private int currentOps;

    public InterestOpsImpl(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        this.currentOps = selectionKey.interestOps();
    }

    @Override
    public void add(int op) {
        int keysOps = selectionKey.interestOps();
        int newOps = currentOps | op;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Adding op <{}> to currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, newOps, selectionKey.interestOps(), selectionKey.channel().validOps());
        }
        currentOps = newOps;

        selectionKey.interestOps(newOps); // CancelledKeyException

        selectionKey.selector().wakeup();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Added op <{}>, currentOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, keysOps, selectionKey.channel().validOps());
        }
    }

    @Override
    public void remove(int op) {
        int newOps = currentOps & ~op;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removing op <{}> from currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, newOps, selectionKey.interestOps(), selectionKey.channel().validOps());
        }
        currentOps = newOps;

        selectionKey.interestOps(newOps); // CancelledKeyException

        selectionKey.selector().wakeup();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removed op <{}>, currentOps <{}>, keyOps <{}>, validOps <{}>", op, currentOps, selectionKey.interestOps(), selectionKey.channel().validOps());
        }
    }

    @Override
    public void removeAll() {
        int keysOps = selectionKey.interestOps();
        int newOps = 0;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removing all currentOps <{}>, newOps <{}>, keyOps <{}>, validOps <{}>", currentOps, newOps, keysOps, selectionKey.channel().validOps());
        }

        selectionKey.interestOps(newOps); // CancelledKeyException

        selectionKey.selector().wakeup();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removed all ops. currentOps <{}>, keyOps <{}>, validOps <{}>", currentOps, keysOps, selectionKey.channel().validOps());
        }
    }
}
