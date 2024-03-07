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
package com.teragrep.rlp_03.context.frame.access;

import java.util.concurrent.Phaser;
import java.util.function.Supplier;

public final class Access implements Supplier<Lease> {
    final Phaser phaser;
    public Access() {
        // initial registered parties is 1, same as calling phaser.register()
        this.phaser = new Phaser(1);
    }

    @Override
    public Lease get() {
        if (phaser.register() < 0) {
            // negative return value on phaser.register() means it was already terminated
            // phaser was already closed by releasing all leases
            throw new IllegalStateException("Access phaser already terminated");
        }

        return new Lease(this);
    }

    public void terminate() throws IllegalStateException {
        // non-negative return value means phaser is not terminated
        if (phaser.arriveAndDeregister() > 0) {
            throw new IllegalStateException("Open leases exist!");
        }
    }

    public void release(Lease lease) {
        if (!lease.isTerminated()) {
            // don't allow releasing an open lease
            throw new IllegalStateException("Cannot release an open lease");
        } else if (phaser.arriveAndDeregister() < 0) {
            // negative return value means phaser was already terminated
            throw new IllegalStateException("Phaser was already terminated");
        }
    }

    public boolean isTerminated() {
        return phaser.isTerminated();
    }
}
