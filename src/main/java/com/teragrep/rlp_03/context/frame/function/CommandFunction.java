package com.teragrep.rlp_03.context.frame.function;

import java.nio.ByteBuffer;
import java.util.LinkedList;
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
