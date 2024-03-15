/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021, 2024  Suomen Kanuuna Oy
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
package com.teragrep.rlp_03.delegate.event;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RelpEventSyslog extends RelpEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpEventSyslog.class);

    private final Consumer<FrameContext> cbFunction;


    public RelpEventSyslog(Consumer<FrameContext> cbFunction) {
        this.cbFunction = cbFunction;
    }

    @Override
    public void accept(FrameContext frameContext) {
        try {
            List<RelpFrameTX> txFrameList = new ArrayList<>();

            if (frameContext.relpFrame().payload().size() > 0) {
                try {
                    cbFunction.accept(frameContext);
                    txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.RESPONSE, "200 OK"));
                } catch (Exception e) {
                    LOGGER.error("EXCEPTION WHILE PROCESSING SYSLOG PAYLOAD", e);
                    txFrameList.add(createResponse(frameContext.relpFrame(),
                            RelpCommand.RESPONSE, "500 EXCEPTION WHILE PROCESSING SYSLOG PAYLOAD"));
                }
            } else {
                txFrameList.add(createResponse(frameContext.relpFrame(), RelpCommand.RESPONSE, "500 NO PAYLOAD"));

            }
            frameContext.connectionContext().relpWrite().accept(txFrameList);
        } finally {
            frameContext.relpFrame().close();
        }
    }

    @Override
    public void close() throws Exception {
        if (cbFunction instanceof AutoCloseable) {
            ((AutoCloseable) cbFunction).close();
        }
    }
}
