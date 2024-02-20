package com.teragrep.rlp_03.context.frame.access;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RentalTest {

    @Test
    public void testAccess() {
        Rental rental = new Rental();

        Assertions.assertFalse(rental.terminated());

        Lease leaseOut;
        try (Lease lease = rental.get()) {
            leaseOut = lease;
            Assertions.assertTrue(lease.isOpen());
            // try-with-resources AutoCloses
        }
        Assertions.assertFalse(leaseOut.isOpen());

        Assertions.assertThrows(IllegalStateException.class, () -> rental.accept(leaseOut));

        rental.close();

        Assertions.assertTrue(rental.terminated());

        Assertions.assertThrows(IllegalStateException.class, rental::get);

        Assertions.assertThrows(IllegalStateException.class, () -> rental.accept(leaseOut));
    }
}
