package com.teragrep.rlp_03.delegate.relp;

import com.teragrep.rlp_01.RelpCommand;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.FrameContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class RelpEventSyslog extends RelpEvent {

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
