package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.function.TransactionFunction;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FragmentToIntTest {
    @Test
    public void fragmentToIntTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        Fragment fragment = new FragmentImpl(transactionFunction);

        String txn = "12345 ";
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();

        fragment.accept(byteBuffer);

        assertEquals(12345, fragment.toInt());
    }

    @Test
    public void fragmentToIntOverflowTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        Fragment fragment = new FragmentImpl(transactionFunction);

        String txn = String.valueOf(Integer.MAX_VALUE+1).concat(" ");
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();

        assertThrows(IllegalArgumentException.class,
                () -> fragment.accept(byteBuffer), "payloadLength too long");
    }

    @Test
    public void fragmentToIntMaxValueTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        Fragment fragment = new FragmentImpl(transactionFunction);

        String txn = String.valueOf(Integer.MAX_VALUE).concat(" ");
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();

        fragment.accept(byteBuffer);
        assertEquals(Integer.MAX_VALUE, fragment.toInt());
    }

    @Test
    public void charArrayToIntTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        FragmentImpl fragment = new FragmentImpl(transactionFunction);
        assertEquals(1709, fragment.charArrayToInt(new char[]{'1', '7', '0', '9'}));
    }

    @Test
    public void charArrayToIntNonNumericTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        FragmentImpl fragment = new FragmentImpl(transactionFunction);
        assertThrows(IllegalStateException.class, () -> fragment.charArrayToInt(new char[]{'1', '7', 'A', '5'}),
                "Unexpected character encountered: A");
    }

    @Test
    public void charArrayToIntOverflowTest() {
        TransactionFunction transactionFunction = new TransactionFunction();
        FragmentImpl fragment = new FragmentImpl(transactionFunction);
        // build overflowing char array
        final String base = String.valueOf((long) Integer.MAX_VALUE + 1L);
        assertThrows(IllegalStateException.class, () -> fragment.charArrayToInt(base.toCharArray()),
                "Integer overflow!");
    }
}
