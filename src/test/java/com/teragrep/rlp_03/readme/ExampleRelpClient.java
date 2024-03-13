/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021,2024  Suomen Kanuuna Oy
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
package com.teragrep.rlp_03.readme;

import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * ExampleRelpClient using rlp_01 for demonstration
 */
public class ExampleRelpClient {
    private final int port;

    public ExampleRelpClient(int port) {
        this.port = port;
    }

    public void send(String record) {
        RelpConnection relpConnection = new RelpConnection();
        try {
            relpConnection.connect("localhost", port);
        } catch (IOException | TimeoutException exception) {
            throw new RuntimeException(exception);
        }

        RelpBatch relpBatch = new RelpBatch();
        relpBatch.insert(record.getBytes(StandardCharsets.UTF_8));

        while (!relpBatch.verifyTransactionAll()) {
            relpBatch.retryAllFailed();
            try {
                relpConnection.commit(relpBatch);
            } catch (IOException | TimeoutException exception) {
                throw new RuntimeException(exception);
            }
        }
        try {
            relpConnection.disconnect();
        } catch (IOException | TimeoutException exception) {
            throw new RuntimeException(exception);
        } finally {
            relpConnection.tearDown();
        }
    }
}
