package com.teragrep.rlp_03.context.frame.function;

import com.teragrep.rlp_01.RelpCommand;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

public class CommandFunction implements BiFunction<ByteBuffer, ByteBuffer, Boolean> {

    //private Set<String> relpCommands;

    CommandFunction() {
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
    public Boolean apply(ByteBuffer input, ByteBuffer buffer) {
        boolean rv = false;
        while(input.hasRemaining()) {
            byte b = input.get();
            if (b == ' ') {
                rv = true;
            }
            else {
                if (buffer.position() == buffer.capacity()) {
                    throw new IllegalArgumentException("command too long");
                }
                buffer.put(b);
            }
        }
        return rv;
    }
}
