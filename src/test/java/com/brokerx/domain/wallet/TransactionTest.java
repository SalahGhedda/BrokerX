package com.brokerx.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private Transaction createPendingTransaction() {
        return new Transaction(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.valueOf(100.0),
            "DEPOSIT",
            "PENDING",
            "idem",
            Instant.now()
        );
    }

    @Test
    void testTransactionCreation() {
        Transaction tx = createPendingTransaction();

        assertEquals("DEPOSIT", tx.getType());
        assertEquals("PENDING", tx.getState());
        assertTrue(tx.getAmount().compareTo(BigDecimal.valueOf(100.0)) == 0);
    }

    @Test
    void testSettleTransaction() {
        Transaction tx = createPendingTransaction();
        tx.setState("SETTLED");

        assertEquals("SETTLED", tx.getState());
    }

    @Test
    void testFailTransaction() {
        Transaction tx = createPendingTransaction();
        tx.setState("FAILED");

        assertEquals("FAILED", tx.getState());
    }
}
