package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.function.TransactionFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FragmentTest {

    @Test
    public void testFragment() {
        TransactionFunction transactionFunction = new TransactionFunction();
        Fragment fragment = new FragmentImpl(transactionFunction);

        Assertions.assertFalse(fragment.isComplete());

        // these must throw because it's not complete
        Assertions.assertThrows(IllegalStateException.class, fragment::size);
        Assertions.assertThrows(IllegalStateException.class, fragment::toBytes);
        Assertions.assertThrows(IllegalStateException.class, fragment::toString);
        Assertions.assertThrows(IllegalStateException.class, fragment::toInt);
        Assertions.assertThrows(IllegalStateException.class, fragment::toFragmentWrite);
        Assertions.assertThrows(IllegalStateException.class, fragment::toFragmentByteStream);

        String txn = "123 ";
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();


        fragment.accept(byteBuffer);
        Assertions.assertTrue(fragment.isComplete());
        Assertions.assertEquals(3, fragment.size());
        
        // conversions
        Assertions.assertArrayEquals(new byte[]{49,50,51}, fragment.toBytes());
        Assertions.assertEquals("123", fragment.toString());
        Assertions.assertEquals(123, fragment.toInt());
        // TODO fragment.toFragmentWrite().write()
        FragmentByteStream fragmentByteStream = fragment.toFragmentByteStream();

        // FragmentByteStream
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 49), fragmentByteStream.get());
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 50), fragmentByteStream.get());
        Assertions.assertTrue(fragmentByteStream.next());
        Assertions.assertEquals(new Byte((byte) 51), fragmentByteStream.get());
        Assertions.assertFalse(fragmentByteStream.next());

    }
}
