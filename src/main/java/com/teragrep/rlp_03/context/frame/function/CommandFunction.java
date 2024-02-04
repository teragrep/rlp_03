package com.teragrep.rlp_03.context.frame.function;

import com.teragrep.rlp_01.RelpCommand;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiFunction;

public class CommandFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    //private Set<String> relpCommands;
    private final int maximumCommandLength;

    private final Set<String> enabledCommands;

    public CommandFunction() {
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
        maximumCommandLength = maximumLength;
    }

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
            checkOverSize(input, bufferSliceList);
            if (b == ' ') {
                rv = true;
                break;
            }
        }

        ByteBuffer bufferSlice;
        if (rv) {
            // adjust limit so that bufferSlice contains only this data, without the terminating ' '
            bufferSlice = (ByteBuffer) input.duplicate().limit(input.position() - 1);

        }
        else {
            bufferSlice = input.duplicate();
        }
        bufferSlice.rewind();
        bufferSliceList.add(bufferSlice);

        return rv;
    }

    private void checkOverSize(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
        long currentLength = 0;
        for (ByteBuffer slice : bufferSliceList) {
            currentLength = currentLength + slice.limit();
        }
        if (currentLength + input.position() > maximumCommandLength) {
            throw new IllegalArgumentException("command too long");
        }
    }
}
