package com.teragrep.rlp_03.context.frame.access;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AccessTest {

    @Test
    public void testAccess() {
        Access access = new Access();

        Assertions.assertFalse(access.terminated());

        Lease leaseOut;
        try (Lease lease = access.get()) {
            leaseOut = lease;
            Assertions.assertTrue(lease.isOpen());
            // try-with-resources AutoCloses
        }
        Assertions.assertFalse(leaseOut.isOpen());

        access.terminate();

        Assertions.assertTrue(access.terminated());

        Assertions.assertThrows(IllegalStateException.class, access::get);

        Assertions.assertThrows(IllegalStateException.class, () -> access.release(leaseOut));
    }
}
