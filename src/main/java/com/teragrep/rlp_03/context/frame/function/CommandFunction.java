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

package com.teragrep.rlp_03.context.frame.function;

import com.teragrep.rlp_01.RelpCommand;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiFunction;

public class CommandFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    private static final int maximumCommandLength = 12; // serverclose + space

    // private final Set<String> enabledCommands;

    public CommandFunction() {
        /*
        this.enabledCommands = new HashSet<>();

        this.enabledCommands.add("open");
        this.enabledCommands.add("close");
        this.enabledCommands.add("abort");
        this.enabledCommands.add("serverclose");
        this.enabledCommands.add("syslog");
        this.enabledCommands.add("rsp");

        int maximumLength = 0;
        for (String command : enabledCommands) {
            if (command.length() > maximumLength) {
                maximumLength = command.length();
            }
        }
        maximumCommandLength = maximumLength + 1; // space
         */
    }

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {

        ByteBuffer slice = input.slice();
        int bytesRead = 0;
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
            bytesRead++;
            checkOverSize(bytesRead, bufferSliceList);
            if (b == ' ') {
                // remove ' ' from the input as it's complete
                ((ByteBuffer) slice).limit(bytesRead - 1);
                rv = true;
                break;
            }
        }

        bufferSliceList.add(slice);

        return rv;
    }

    private void checkOverSize(int bytesRead, LinkedList<ByteBuffer> bufferSliceList) {
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + ((ByteBuffer) slice).limit();
        }

        currentLength = currentLength +  bytesRead;
        if (currentLength > maximumCommandLength) {
            throw new IllegalArgumentException("command too long");
        }
    }
}
