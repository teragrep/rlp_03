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

package com.teragrep.rlp_03.context.frame.fragment;

import java.nio.ByteBuffer;
import java.util.LinkedList;


// TODO tests
class FragmentByteStreamImpl implements FragmentByteStream {

    private final LinkedList<ByteBuffer> bufferCopies;

    private ByteBuffer currentBuffer;

    private int limit;
    private int position;

    private static final ByteBuffer byteBufferStub = ByteBuffer.allocateDirect(0);


    public FragmentByteStreamImpl(LinkedList<ByteBuffer> bufferCopies) {
        this.bufferCopies = bufferCopies;
        this.limit = 0;
        this.position = -1;
        this.currentBuffer = byteBufferStub;
    }

    private boolean changeBuffer() {
        boolean rv = false;
        if (!bufferCopies.isEmpty()) {
            currentBuffer = bufferCopies.pop();
            limit = currentBuffer.limit();
            position = -1;
            rv = true;
        }
        return rv;
    }

    @Override
    public Byte get() {
        return currentBuffer.get(position);
    }

    @Override
    public boolean next() {
        boolean rv = false;
        if (position < limit - 1) {
            position++;
            rv = true;
        }
        else {
            while (changeBuffer()) {
                if (position < limit - 1) {
                    position++;
                    rv = true;
                    break;
                }
            }
        }
        return rv;
    }
}
