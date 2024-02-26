package com.teragrep.rlp_03.context.frame.fragment;

import com.teragrep.rlp_03.context.frame.function.TransactionFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named="runFragmentPerformanceTest", matches = "true")
public class ManualFragmentPerformanceTest {
    private final int loops = 100_000_000;

    @Test
    public void positiveToIntPerformanceTest() {
        int expected = 123_456_789;
        Fragment fragment = createFragment(String.valueOf(expected));
        Instant start = Instant.now();
        for(int i=0; i<loops; i++) {
            assertEquals(expected, fragment.toInt());
        }
        Instant end = Instant.now();
        printStats("positiveToIntPerformanceTest", start, end);
    }

    @Test
    public void negativeToIntPerformanceTest() {
        int expected = -123_456_789;
        Fragment fragment = createFragment(String.valueOf(expected));
        Instant start = Instant.now();
        for(int i=0; i<loops; i++) {
            assertEquals(expected, fragment.toInt());
        }
        Instant end = Instant.now();
        printStats("negativeToIntPerformanceTest", start, end);
    }

    @Test
    public void toStringPerformanceTest() {
        String expected = "123456789";
        Fragment fragment = createFragment(expected);
        Instant start = Instant.now();
        for(int i=0; i<loops; i++) {
            assertEquals(expected, fragment.toString());
        }
        Instant end = Instant.now();
        printStats("toStringPerformanceTest", start, end);
    }

    @Test
    public void toBytesPerformanceTest() {
        String input = "123456789";
        byte[] expected = input.getBytes();
        Fragment fragment = createFragment(input);
        Instant start = Instant.now();
        for(int i=0; i<loops; i++) {
            assertArrayEquals(expected, fragment.toBytes());
        }
        Instant end = Instant.now();
        printStats("toBytesPerformanceTest", start, end);
    }

    private Fragment createFragment(String input) {
        TransactionFunction transactionFunction = new TransactionFunction();
        FragmentImpl fragment = new FragmentImpl(transactionFunction);
        String txn = String.valueOf(input).concat(" ");
        byte[] txnBytes = txn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(txnBytes.length);
        byteBuffer.put(txnBytes);
        byteBuffer.flip();
        fragment.accept(byteBuffer);
        return fragment;
    }

    private void printStats(String caller, Instant start, Instant end) {
        float elapsed = (float) Duration.between(start, end).toMillis()/1000;
        int eps = (int) (loops/elapsed);
        System.out.printf("[%s] Executed %,d loops in %.2f seconds (%,d eps)%n", caller, loops, elapsed, eps);
    }
}
