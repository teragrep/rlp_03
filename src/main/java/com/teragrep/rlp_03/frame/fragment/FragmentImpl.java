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
package com.teragrep.rlp_03.frame.fragment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class FragmentImpl implements Fragment {

    private final LinkedList<ByteBuffer> bufferSliceList;

    // BiFunction is the parser function that takes: input, storageList, return value
    private final BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> parseRule;

    private final AtomicBoolean isComplete;

    public FragmentImpl(BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> parseRule) {
        this.bufferSliceList = new LinkedList<>();
        this.parseRule = parseRule;

        this.isComplete = new AtomicBoolean();
    }

    @Override
    public void accept(ByteBuffer input) {
        if (isComplete.get()) {
            throw new IllegalStateException("Fragment is complete, can not accept more.");
        }

        if (parseRule.apply(input, bufferSliceList)) { // TODO change to buffers and scatter gather pattern?
            isComplete.set(true);
        }
    }

    @Override
    public boolean isStub() {
        return false;
    }

    @Override
    public boolean isComplete() {
        return isComplete.get();
    }

    @Override
    public byte[] toBytes() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }

        int totalBytes = 0;
        for (ByteBuffer slice : bufferSliceList) {
            totalBytes = totalBytes + slice.remaining();
        }
        byte[] bytes = new byte[totalBytes];

        int copiedBytes = 0;
        for (ByteBuffer slice : bufferSliceList) {
            int remainingBytes = slice.remaining();
            slice.asReadOnlyBuffer().get(bytes, copiedBytes, remainingBytes);
            copiedBytes = copiedBytes + remainingBytes;
        }

        return bytes;
    }

    @Override
    public String toString() {
        byte[] bytes = toBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public int toInt() {
        String integerString = toString();
        return Integer.parseInt(integerString);
    }

    @Override
    public FragmentWrite toFragmentWrite() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        LinkedList<ByteBuffer> bufferCopies = new LinkedList<>();
        for (ByteBuffer buffer : bufferSliceList) {
            bufferCopies.add(buffer.asReadOnlyBuffer());
        }
        return new FragmentWriteImpl(bufferCopies);
    }

    @Override
    public FragmentByteStream toFragmentByteStream() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        LinkedList<ByteBuffer> bufferCopies = new LinkedList<>();
        for (ByteBuffer buffer : bufferSliceList) {
            bufferCopies.add(buffer.asReadOnlyBuffer());
        }
        return new FragmentByteStreamImpl(bufferCopies);
    }

    @Override
    public long size() {
        if (!isComplete.get()) {
            throw new IllegalStateException("Fragment incomplete!");
        }
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + ((ByteBuffer) slice).limit();
        }
        return currentLength;
    }
}
