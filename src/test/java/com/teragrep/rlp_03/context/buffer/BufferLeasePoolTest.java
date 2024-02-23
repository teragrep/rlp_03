package com.teragrep.rlp_03.context.buffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BufferLeasePoolTest {

    @Test
    public void testPool() {
        BufferLeasePool bufferLeasePool = new BufferLeasePool();
        List<BufferLease> leases = bufferLeasePool.take(1);

        Assertions.assertEquals(1, leases.size());

        Assertions.assertEquals(0, bufferLeasePool.estimatedSize()); // none in the pool

        BufferLease lease = leases.get(0);

        Assertions.assertFalse(lease.isStub());

        Assertions.assertFalse(lease.isRefCountZero()); // initially 1 refs

        Assertions.assertEquals(1, lease.refs()); // check initial 1 ref

        lease.addRef();

        Assertions.assertEquals(2, lease.refs());

        lease.buffer().put((byte) 'x');

        Assertions.assertEquals(1, lease.buffer().position());

        lease.buffer().flip();

        Assertions.assertEquals(0, lease.buffer().position());

        Assertions.assertEquals(1, lease.buffer().limit());

        Assertions.assertEquals((byte) 'x', lease.buffer().get());

        Assertions.assertEquals(1, lease.buffer().position());

        Assertions.assertEquals(2, lease.refs());

        lease.removeRef();


        Assertions.assertFalse(lease.isRefCountZero()); // initial ref must be still in place

        Assertions.assertEquals(1, lease.refs()); // initial ref must be still in

        bufferLeasePool.offer(lease); // removes initial ref

        Assertions.assertEquals(1, bufferLeasePool.estimatedSize()); // the one offered must be there

        Assertions.assertTrue(lease.isRefCountZero()); // no refs

        Assertions.assertEquals( lease.buffer().capacity(), lease.buffer().limit());

        Assertions.assertEquals(0, lease.buffer().position());

        bufferLeasePool.close();

        Assertions.assertEquals(0, bufferLeasePool.estimatedSize());
    }
}
