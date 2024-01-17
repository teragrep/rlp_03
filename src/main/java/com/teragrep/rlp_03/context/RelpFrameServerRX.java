package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.AbstractRelpFrame;
import com.teragrep.rlp_01.RelpFrameTX;
import com.teragrep.rlp_03.TransportInfo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RelpFrameServerRX extends AbstractRelpFrame {

    /**
     * PAYLOAD
     */

    private final ConnectionContext connectionContext;

    RelpFrameServerRX(
            int txID,
            String command,
            int dataLength,
            ByteBuffer src,
            ConnectionContext connectionContext
    ) {
        super(txID, command, dataLength);
        this.data = new byte[src.remaining()];
        this.connectionContext = connectionContext;
        src.get(this.data);
    }

    public byte[] getData() {
        return data;
    }

    /**
     An override for the toString() method. Builds a string (including spaces and
     newline trailer at the end) from the RELP response frame.
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append( this.transactionNumber );
        stringBuilder.append( ' ' );
        stringBuilder.append( this.command );
        stringBuilder.append( ' ' );
        stringBuilder.append( this.dataLength );
        if( this.data != null ) {
            stringBuilder.append( ' ' );
            stringBuilder.append( new String(this.data, StandardCharsets.UTF_8) );
        }
        stringBuilder.append( '\n' );
        return stringBuilder.toString();
    }

    /**
     * TransportInfo for payload processing aid
     */

    public TransportInfo getTransportInfo() {
        return connectionContext.socket().getTransportInfo();
    }

    public void sendResponse(List<RelpFrameTX> responses) {
        connectionContext.relpWrite().accept(responses);
    }
}
