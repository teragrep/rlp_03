package com.teragrep.rlp_03.context.frame.function;

import com.teragrep.rlp_01.RelpCommand;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiFunction;

public class CommandFunction implements BiFunction<ByteBuffer, LinkedList<ByteBuffer>, Boolean> {

    //private Set<String> relpCommands;
    private final int maximumCommandLength = 11;


    public CommandFunction() {
        /*
        this.relpCommands = new HashSet<>();
        this.relpCommands.add(RelpCommand.OPEN);
        this.relpCommands.add(RelpCommand.CLOSE);
        this.relpCommands.add(RelpCommand.ABORT);
        this.relpCommands.add(RelpCommand.SERVER_CLOSE);
        this.relpCommands.add(RelpCommand.SYSLOG);
        this.relpCommands.add(RelpCommand.RESPONSE);
         */
    }

    @Override
    public Boolean apply(ByteBuffer input, LinkedList<ByteBuffer> bufferSliceList) {
        boolean rv = false;
        while (input.hasRemaining()) {
            byte b = input.get();
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
}
